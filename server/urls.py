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

from django.conf.urls.defaults import *

import quiz_poll_api
import quiz_poll_player

handler500 = 'djangotoolbox.errorviews.server_error'

urlpatterns = patterns('',
    ('^_ah/warmup$', 'djangoappengine.views.warmup'),
    ('^$', 'django.views.generic.simple.direct_to_template',
     {'template': 'home.html'}),
    (r'^qp_api/documents/(?P<collection_id>.+)$',
     quiz_poll_api.DocumentsInCollection, {}, 'DocumentsInCollection'),
    (r'^qp_api/quiz/leaderboard/(?P<document_id>.+)/(?P<sheet_id>.+)$',
     quiz_poll_api.QuizLeaderboard, {}, 'QuizLeaderboard'),
    (r'^qp_api/quiz/submit$', quiz_poll_api.QuizSubmit, {}, 'QuizSubmit'),
    (r'^qp_api/quiz/(?P<document_id>.+)$', quiz_poll_api.Quiz, {}, 'Quiz'),
    (r'^qp_api/poll/submit$', quiz_poll_api.PollSubmit, {}, 'PollSubmit'),
    (r'^qp_api/poll/status/invalidate/(?P<document_id>.+)$',
     quiz_poll_api.PollStatusInvalidate, {}, 'PollStatusInvalidate'),
    (r'^qp_api/poll/status/(?P<document_id>.+)/(?P<sheet_id>.+)$',
     quiz_poll_api.PollStatus, {}, 'PollStatus'),
    (r'^qp_api/poll/(?P<document_id>.+)$', quiz_poll_api.Poll, {}, 'Poll'),
    (r'^quiz/(?P<document_id>.+)$', quiz_poll_player.Quiz, {}, 'QuizPlayer'),
    (r'^poll/(?P<document_id>.+)$', quiz_poll_player.Poll, {}, 'PollPlayer'),
)
