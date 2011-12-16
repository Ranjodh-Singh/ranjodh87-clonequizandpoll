#!/usr/bin/python2.7
# Copyright 2011 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS-IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


"""JSON API for Quiz&Poll data from spreadsheets.

It is used for Mobile Quiz Games and Mobile Polling to avoid clients having
permissions to spreadsheets (which can cause cheating).
Both Android client and Javascript client are using this API.
"""

import functools
import random

from atom import http_core

from django import http
from django.utils import simplejson

from gdata import client as gdata_client
from gdata import gauth
from gdata.docs import client as docs_client
from gdata.spreadsheets import client as spreadsheets_client
from gdata.spreadsheets import data as spreadsheets_data

from google.appengine.api import app_identity
from google.appengine.api import memcache
from google.appengine.api import users

# OAuth scopes for auth
SCOPE_DOCUMENTS = 'https://docs.google.com/feeds/'
SCOPE_SPREADSHEETS = 'https://spreadsheets.google.com/feeds/'

# Types of documents in the document list
DOCUMENT_TYPE_COLLECTION = 0
DOCUMENT_TYPE_QUIZ = 1

# If question wasn't answered in stats
NOT_ANSWERED_YET = '-not answered yet-'

# Memcache keys
MEMCACHE_KEY_POLL_STATUS = 'poll_status_%s'
MEMCACHE_KEY_POLL = 'poll_%s'


class Error(Exception):
  """Base class for exceptions in this module."""
  pass


class FormatError(Error):
  """Exception raised when spreadsheet is not formatted right."""


class PermissionError(Error):
  """Exception raised when server doesn't have permission to spreadsheet."""


class NeedsUpdateError(Error):
  """Exception raised when force updating client."""


def DocsClientDecorator(func):
  """Decorator for other functions. Creates gdata client and handles errors.

  Args:
    func: Function to decorate.

  Returns:
    Decorated function.
  """

  @functools.wraps(func)
  def Wrap(request, *args, **kwargs):
    """Creates gdata client and handles errors."""
    if 'documents' in request.path:
      scope = SCOPE_DOCUMENTS
    else:
      scope = SCOPE_SPREADSHEETS
    client = _GetAuthenticatedClient(scope)
    kwargs['client'] = client
    # Submit is done by POST containing JSON
    if 'submit' in request.path:
      kwargs['json_data'] = request.raw_post_data
    # Force update based on user-agent is used only in Poll function
    if ('poll' in request.path and 'submit' not in request.path and
        'status' not in request.path):
      kwargs['user_agent'] = request.META['HTTP_USER_AGENT']
    try:
      return func(*args, **kwargs)
    except PermissionError:
      return http.HttpResponse(status=403)  # Forbidden
    except FormatError:
      return http.HttpResponse(status=415)  # Unsupported Media Type
    except NeedsUpdateError:
      return http.HttpResponse(status=426)  # Upgrade Required
  return Wrap


@DocsClientDecorator
def DocumentsInCollection(client, collection_id):
  """Lists quiz-related documents in a collection.

  Args:
    client: Docs gdata client
    collection_id: id of collection with quizzes/collections

  Returns:
    JSON representation of documents
  """
  feed = client.GetResources(uri='/feeds/default/private/full/%s'
                             '/contents?showfolders=true' % collection_id)
  # Build JSON response
  data = []
  for entry in feed.entry:
    if entry.GetResourceType() == 'spreadsheet':
      document_type = DOCUMENT_TYPE_QUIZ
    elif entry.GetResourceType() == 'folder':
      document_type = DOCUMENT_TYPE_COLLECTION
    else:
      continue  # Skip other file types
    title = _FormatQuizName(entry.title.text.encode('UTF-8'))
    document_id = entry.resource_id.text.split(':')[1]
    data.append({'type': document_type, 'title': title,
                 'id': document_id})
  return _OutputJson(data)


@DocsClientDecorator
def Quiz(client, document_id):
  """Creates JSON representation of Mobile Quiz Game spreadsheet."""
  try:
    feed = client.GetWorksheets(document_id)
  except gdata_client.RequestError:
    raise PermissionError
  title = _FormatQuizName(feed.title.text)
  sheet_ids = [entry.GetWorksheetId() for entry in feed.entry]
  if len(sheet_ids) < 4:
    raise FormatError

  # First sheet contains questions
  _, data = _Cells(client, document_id, sheet_ids[0])
  if not data:
    raise FormatError
  questions = []
  for row_number, row in enumerate(data):
    if len(row) < 2:
      raise FormatError
    answers = []
    for cell_number, cell in enumerate(row):
      if cell_number == 0:
        question_text = cell
      else:
        correct = cell.endswith('*')
        answer_text = _FormatAnswer(cell)
        answers.append({'answer_text': answer_text, 'correct': correct,
                        'number': cell_number - 1})
    # Randomize answers
    random.shuffle(answers)
    questions.append({'question_text': question_text, 'answers': answers,
                      'number': row_number})
  # Randomize questions and show just first 10
  random.shuffle(questions)
  questions = questions[:10]
  # Second sheet contains metadata
  _, data = _Cells(client, document_id, sheet_ids[1])
  if not data or len(data[0]) < 2:
    raise FormatError
  description = data[0][0]
  image = data[0][1]
  # Now we have all data, build the quiz
  leaderboard_sheet = sheet_ids[2]
  statistics_sheet = sheet_ids[3]
  quiz = {'title': title, 'description': description, 'image': image,
          'leaderboard_sheet': leaderboard_sheet,
          'statistics_sheet': statistics_sheet, 'document_id': document_id,
          'questions': questions}
  return _OutputJson(quiz)


@DocsClientDecorator
def QuizLeaderboard(client, document_id, sheet_id):
  """Creates JSON representation of Mobile Quiz Game's leaderboard."""
  # Third sheet contains leaderboard
  _, data = _Cells(client, document_id, sheet_id)
  leaderboard = [{'ldap': row[0], 'score': row[1]} for row in data]
  return _OutputJson(leaderboard)


@DocsClientDecorator
def QuizSubmit(client, json_data):
  """Updates both leaderboard and statistics for Mobile Quiz Game.

  Parses JSON representation of completed Mobile Quiz Game, writes leaderboard
  entry in one worksheet, creates statistics from responses and writes that
  statistics into another worksheet.

  Args:
    client: spreadsheet client
    json_data: json string of quiz

  Returns:
    OK response
  """
  quiz = simplejson.loads(json_data)
  # Leaderboard update
  username = _GetUsername()
  feed = client.GetListFeed(quiz['document_id'], quiz['leaderboard_sheet'])
  found_ldap = False
  for entry in feed.entry:
    if entry.get_value('ldap') == username:
      old_score = entry.get_value('score')
      found_ldap = True
      if int(quiz['score']) > int(old_score):
        entry.set_value('score', str(quiz['score']))
        client.Update(entry)
      break
  if not found_ldap:
    new_entry = spreadsheets_data.ListEntry()
    new_entry.set_value('ldap', username)
    new_entry.set_value('score', str(quiz['score']))
    client.AddListEntry(new_entry, quiz['document_id'],
                        quiz['leaderboard_sheet'])
  # Get current statistics
  cells_feed, data = _Cells(client, quiz['document_id'],
                            quiz['statistics_sheet'])
  statistics = []
  for row in data:
    statistic = [row[0], int(row[1]), int(row[2]), row[3]]
    for cell in row[4:]:
      statistic.append(int(cell))  # answers
    statistics.append(statistic)
  # Update statistics based on answers of this student
  for question in quiz['questions']:
    if question['number'] >= len(statistics):
      # Fill in statistics between with default data
      for _ in range(len(statistics), question['number'] + 1):
        statistics.append([NOT_ANSWERED_YET, 0, 0, '0 %'])
    u = statistics[question['number']]
    u[0] = question['question_text']  # Question text
    if question['success'] == True:
      u[1] += 1  # Successes
    else:
      u[2] += 1  # Failures
    failure_rate = int((float(u[2]) / float((u[1] + u[2]))) * 100)
    u[3] = str(failure_rate) + ' %'
    for answer in question['answers']:
      if answer['answered']:
        # add empty answers
        if answer['number'] >= len(u) - 4:
          for _ in range(len(u) - 4, answer['number'] + 1):
            u.append(0)
        u[answer['number'] + 4] += 1
  # Write statistics back to spreadsheet using batch
  # Gdata Python libraries are undocumented, so I use raw protocol instead
  batch = ('<feed xmlns="http://www.w3.org/2005/Atom" '
           'xmlns:batch="http://schemas.google.com/gdata/batch" '
           'xmlns:gs="http://schemas.google.com/spreadsheets/2006">')
  batch_entry = ('<entry><batch:id>%s</batch:id><batch:operation '
                 'type="update"/><id>'
                 'https://spreadsheets.google.com/feeds/cells/%s/%s'
                 '/private/full/%s</id><gs:cell row="%s" col="%s" '
                 'inputValue="%s"/></entry>')
  for row, statistic in enumerate(statistics):
    for col, value in enumerate(statistic):
      r = str(row + 2)
      c = str(col + 1)
      rc = 'R' + r + 'C' + c
      batch += batch_entry % (rc, quiz['document_id'],
                              quiz['statistics_sheet'], rc, r, c, str(value))
  batch += '</feed>'
  batch_link = cells_feed.FindBatchLink()
  # Overwrite existing
  request = http_core.HttpRequest(headers={'If-Match': '*'})
  request.method = 'POST'
  request.AddBodyPart(batch, 'application/atom+xml', len(batch))
  client.Request(uri=batch_link, http_request=request)
  return http.HttpResponse('OK')


@DocsClientDecorator
def Poll(client, document_id, user_agent):
  """Creates JSON representation of Mobile Polling spreadsheet."""
  memcache_key = MEMCACHE_KEY_POLL % document_id
  memcache_value = memcache.get(memcache_key)
  if memcache_value is None:
    try:
      feed = client.GetWorksheets(document_id)
    except gdata_client.RequestError:
      raise PermissionError
    title = _FormatPollName(feed.title.text)
    sheet_ids = [entry.GetWorksheetId() for entry in feed.entry]
    if len(sheet_ids) < 4:
      raise FormatError

    # Forced update for old Android clients
    parts = user_agent.split('/')
    if len(parts) >= 5:
      try:
        version = int(parts[4])
        _, data = _Cells(client, document_id, sheet_ids[2])
        if len(data[0]) >= 4:
          minimal_version = int(data[0][3])
          if version < minimal_version:
            raise NeedsUpdateError
      except TypeError:
        pass  # Version is not a number, skip
      except ValueError:
        pass  # Version is not a number, skip

    # Second sheet contains questions
    _, data = _Cells(client, document_id, sheet_ids[1])
    if not data:
      raise FormatError
    questions = []
    for row_number, row in enumerate(data):
      if len(row) < 4:
        raise FormatError
      answers = []
      for cell_number, cell in enumerate(row):
        if cell_number == 0:
          question_text = cell
        elif cell_number == 1:
          continue  # Image is ignored
        elif cell_number == 2:
          anonymous = (cell == '1')
        else:
          correct = cell.endswith('*')
          answer_text = _FormatAnswer(cell)
          answers.append({'answer_text': answer_text, 'correct': correct,
                          'number': cell_number - 3})
      questions.append({'question_text': question_text,
                        'anonymous': anonymous, 'answers': answers,
                        'number': row_number})
    polling = {'title': title, 'internal_data_sheet': sheet_ids[2],
               'responses_sheet': sheet_ids[3], 'document_id': document_id,
               'questions': questions}
    response = _OutputJson(polling)
    memcache.set(memcache_key, response.content, 30)
    return response
  else:
    # Use cached value
    return http.HttpResponse(memcache_value, 'application/json')


@DocsClientDecorator
def PollStatus(client, document_id, sheet_id):
  """Returns current status of Mobile Polling."""
  memcache_key = MEMCACHE_KEY_POLL_STATUS % document_id
  memcache_value = memcache.get(memcache_key)
  if memcache_value is None:
    # Load it from spreadsheet
    _, data = _Cells(client, document_id, sheet_id)
    # We just need question number in the first cell
    if not data:
      raise FormatError
    response = _OutputJson(data[0][0])
    # Expiration is one hour, because cache is invalidated using HTTP call
    # from spreadsheet
    memcache.set(memcache_key, response.content, 60 * 60)
    return response
  else:
    # Use cached value
    return http.HttpResponse(memcache_value, 'application/json')


def PollStatusInvalidate(unused_request, document_id):
  """Invalidates status memcache when current question is changed.

  This request is not protected by authentication because of URLFetch in
  AppScript doesn't easily allow it. But there is no harm if somebody
  invalidates cache.

  Args:
    document_id: id of the spreadsheet

  Returns:
    OK response
  """
  memcache_key = MEMCACHE_KEY_POLL_STATUS % document_id
  memcache.delete(memcache_key)
  return http.HttpResponse('OK')


@DocsClientDecorator
def PollSubmit(client, json_data):
  """Writes response of the poll into spreadsheet.

  Args:
    client: spreadsheets client
    json_data:  data encoded in json

  Returns:
    OK response
  """
  data = simplejson.loads(json_data)
  if data['anonymous']:
    username = 'Anonymous'
  else:
    username = _GetUsername()
  if data['success']:
    success = str(1)
  else:
    success = str(0)
  # Check if student already answered this question
  already_answered = False
  if not data['anonymous']:
    _, responses = _Cells(client, data['document_id'], data['sheet_id'])
    for row in responses:
      if row[0] == username and row[1] == str(data['question_number']):
        already_answered = True
        break

  if not already_answered:
    # Add to spreadsheet
    new_entry = spreadsheets_data.ListEntry()
    new_entry.set_value('ldap', username)
    new_entry.set_value('question', str(data['question_number']))
    new_entry.set_value('answer', data['answers'])
    new_entry.set_value('success', success)
    client.AddListEntry(new_entry, data['document_id'], data['sheet_id'])
  return http.HttpResponse('OK')


def _GetAuthenticatedClient(scope):
  """Creates authenticated client for docs or spreadsheets gdata API.

  It uses Appengine's robot account. Spreadsheets must be shared with user
  quiz-n-poll@appspot.gserviceaccount.com

  Args:
    scope: OAuth2 scope

  Returns:
    Authenticated client
  """

  if scope == SCOPE_DOCUMENTS:
    client = docs_client.DocsClient()
  else:
    client = spreadsheets_client.SpreadsheetsClient()
  token = app_identity.get_access_token(scope)[0]
  client.auth_token = gauth.OAuth2Token(None, None, None, None, None,
                                        access_token=token)
  return client


def _Cells(client, document_id, worksheet_id):
  """Reads all cells in a worksheet into two-dimensional array."""
  feed = client.GetCells(document_id, worksheet_id)
  prev_row_number = -1
  prev_col_number = -1
  data = []
  row = None
  for entry in feed.entry:
    row_number = int(entry.cell.row)
    col_number = int(entry.cell.col)
    if row_number == prev_row_number:
      # Add empty columns
      diff = col_number - prev_col_number
      if diff > 1:
        for _ in range(1, diff):
          row.append('')
      # Add item to the row
      row.append(entry.cell.input_value)
    else:
      # Create new row
      if row is not None:
        data.append(row)
      row = []
      row.append(entry.cell.input_value)
    prev_col_number = col_number
    prev_row_number = row_number
  # Last row
  data.append(row)
  # Remove first row, just description of columns
  data.pop(0)
  return feed, data


def _OutputJson(simple_data):
  """Creates HTTP response in JSON format."""
  content = simplejson.dumps(simple_data)
  return http.HttpResponse(content, 'application/json')


def _FormatQuizName(name):
  """Removes [Q] from spreadsheet name."""
  return name.replace('[Q]', '').strip()


def _FormatPollName(name):
  """Removes [P] from spreadsheet name."""
  return name.replace('[P]', '').strip()


def _FormatAnswer(name):
  """Removes * and spreadsheet true/false formatting."""
  name = name.rstrip('*')
  if name.lower() == 'true' or name.lower() == 'false':
    name = name.capitalize()
  return name


def _GetUsername():
  """Figures out username of currently logged user."""
  user = users.get_current_user()
  return user.email().split('@')[0]
