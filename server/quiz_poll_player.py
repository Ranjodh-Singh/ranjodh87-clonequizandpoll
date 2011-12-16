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


"""Web Player for Quiz&Poll.

It offers similar features to Android app by communicating with the same API
via AJAX.
"""

from django import shortcuts
from django import template


def Quiz(request, document_id):
  """Displays static webpage on Android phones and web player on others."""
  data = {'android': _IsAndroid(request), 'document_id': document_id}
  context = template.RequestContext(request, data)
  return shortcuts.render_to_response('quiz.html', context)


def Poll(request, document_id):
  """Displays static webpage on Android phones and web player on others."""
  data = {'android': _IsAndroid(request), 'document_id': document_id}
  context = template.RequestContext(request, data)
  return shortcuts.render_to_response('poll.html', context)


def _IsAndroid(request):
  """Detects Android browser."""
  return 'Android' in request.META['HTTP_USER_AGENT']
