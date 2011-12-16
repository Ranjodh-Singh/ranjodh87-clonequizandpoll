/*
 Copyright 2011 Google Inc. All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License');
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS-IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

/**
 * @fileoverview Javascript for Quiz&Poll web player.
 */

goog.provide('qp_player');

goog.require('goog.Timer');
goog.require('goog.dom');
goog.require('goog.json');
goog.require('goog.net.XhrIo');
goog.require('goog.style');

var POLLING_CHECKING_INTERVAL = 3000;
var POLLING_STATUS_CLOSED = -3;
var POLLING_STATUS_WAITING = -2;
var QUIZ_TIME_FOR_ANSWER = 120; // in seconds
var QUIZ_WRONG_ANSWER_PENALIZATION = 100; // -100 when answer incorrectly

/**
 * All poll data converted from JSON coming from server API.
 *
 * @type {Array.<Object>}
 */
qp_player.poll = null;

/**
 * All quiz data converted from JSON coming from server API.
 *
 * @type {Array.<Object>}
 */
qp_player.quiz = null;

/**
 * Current question in the quiz.
 *
 * @type {int}
 */
qp_player.currentQuestion = 0;

/**
 * Timer used for periodic checking.
 *
 * @type {goog.Timer}
 */
qp_player.timer = null;

/**
 * Whether current question has been submitted or not.
 *
 * @type {boolean}
 */
qp_player.submitted = false;

/**
 * Remaining time for the question.
 *
 * @type {int}
 */
qp_player.remainingTime = 0;

/**
 * Current score.
 *
 * @type {int}
 */
qp_player.score = 0;

/**
 * Loads poll data from the API.
 *
 * @param {string} documentId ID of the poll spreadsheet.
 * @export
 */
qp_player.loadPoll = function(documentId) {
  goog.net.XhrIo.send('/qp_api/poll/' + documentId, function(e) {
    qp_player.poll = e.target.getResponseJson();
    goog.dom.getElement('title').textContent = qp_player.poll['title'];
    qp_player.checkStatus();
    qp_player.startTimer();
  });
  goog.events.listen(goog.dom.getElement('submit_answer'),
    goog.events.EventType.CLICK, qp_player.submitPollAnswer);
};

/**
 * Loads quiz data from the API.
 *
 * @param {string} documentId ID of the quiz spreadsheet.
 * @export
 */
qp_player.loadQuiz = function(documentId) {
  qp_player.show(goog.dom.getElement('status'));
  goog.net.XhrIo.send('/qp_api/quiz/' + documentId, function(e) {
    qp_player.quiz = e.target.getResponseJson();
    goog.dom.getElement('title').textContent = qp_player.quiz['title'];
    qp_player.showQuizInfo();
  });
  goog.events.listen(goog.dom.getElement('start_quiz'),
    goog.events.EventType.CLICK, qp_player.showNextQuestion);
  goog.events.listen(goog.dom.getElement('show_leaderboard'),
    goog.events.EventType.CLICK, qp_player.showLeaderboard);
  goog.events.listen(goog.dom.getElement('submit_answer'),
    goog.events.EventType.CLICK, qp_player.submitQuizAnswer);
  goog.events.listen(goog.dom.getElement('back'),
    goog.events.EventType.CLICK, qp_player.reload);
};

/**
 * Shows image and description of the quiz.
 */
qp_player.showQuizInfo = function() {
  qp_player.hide(goog.dom.getElement('status'));
  qp_player.show(goog.dom.getElement('info'));
  qp_player.hide(goog.dom.getElement('question'));
  qp_player.hide(goog.dom.getElement('leaderboard'));
  goog.dom.getElement('description').textContent =
    qp_player.quiz['description'];
  goog.dom.getElement('quiz_image').src = qp_player.quiz['image'];
  qp_player.score = 0;
  qp_player.currentQuestion = 0;
  qp_player.remainingTime = QUIZ_TIME_FOR_ANSWER;
};

/**
 * Shows leaderboard of the quiz.
 */
qp_player.showLeaderboard = function() {
  qp_player.hide(goog.dom.getElement('status'));
  qp_player.hide(goog.dom.getElement('info'));
  qp_player.hide(goog.dom.getElement('question'));
  qp_player.show(goog.dom.getElement('leaderboard'));
  goog.dom.getElement('title').textContent = qp_player.quiz['title'];
  if (qp_player.quiz['score']) {
    qp_player.show(goog.dom.getElement('last_score'));
    goog.dom.getElement('last_score_value').textContent =
      qp_player.quiz['score'];
    goog.dom.getElement('back').value = 'Try again';
  } else {
    qp_player.hide(goog.dom.getElement('last_score'));
    goog.dom.getElement('back').value = 'Back';
  }

  goog.net.XhrIo.send('/qp_api/quiz/leaderboard/' +
    qp_player.quiz['document_id'] + '/' + qp_player.quiz['leaderboard_sheet'],
    function(e) {
      var leaderboard = e.target.getResponseJson();
      var l_entries = goog.dom.getElement('leaderboard_entries');
      goog.dom.removeChildren(l_entries);
      // Sort leaderboard entries
      leaderboard.sort(function(a, b) {
        return b['score'] - a['score'];
      });
      for (var i = 0, entry; entry = leaderboard[i]; i++) {
        var l_entry = goog.dom.createDom('li', {}, entry['ldap'] + ' (' +
          entry['score'] + ')');
        goog.dom.appendChild(l_entries, l_entry);
    }
  });
};

/**
 * Goes to the next question in quiz.
 */
qp_player.showNextQuestion = function() {
  qp_player.hide(goog.dom.getElement('status'));
  qp_player.hide(goog.dom.getElement('info'));
  qp_player.show(goog.dom.getElement('question'));
  qp_player.hide(goog.dom.getElement('leaderboard'));
  goog.dom.classes.set(goog.dom.getElement('answers'), '');
  if (qp_player.currentQuestion < qp_player.quiz['questions'].length) {
    var question = qp_player.quiz['questions'][qp_player.currentQuestion];
    qp_player.showQuestion(question);
    goog.dom.getElement('title').textContent = qp_player.quiz['title'] + ' ' +
      (qp_player.currentQuestion + 1) + '/10';
    // Start timer
    if (qp_player.timer) {
      qp_player.timer.dispose();
    }
    qp_player.timer = new goog.Timer(1000);
    qp_player.timer.start();
    goog.events.listen(qp_player.timer, goog.Timer.TICK, qp_player.updateTimer);
  } else {
    qp_player.submitQuiz();
  }
};

/**
 * Updates remaining time in timer.
 */
qp_player.updateTimer = function() {
  qp_player.remainingTime--;
  goog.dom.getElement('timer_value').textContent = qp_player.remainingTime +
    ' s';
  if (qp_player.remainingTime <= 0) {
    qp_player.submitQuizAnswer();
  }
};

/**
 * Submits answer in the quiz, doing some effects.
 */
qp_player.submitQuizAnswer = function() {
  qp_player.stopTimer();
  var question = qp_player.quiz['questions'][qp_player.currentQuestion];
  var correct = qp_player.isAnswerCorrect(question);
  // Update score
  if (correct) {
    qp_player.score += qp_player.remainingTime;
  } else {
    qp_player.score -= QUIZ_WRONG_ANSWER_PENALIZATION;
  }
  goog.dom.getElement('score_value').textContent = qp_player.score;
  // Update timer
  qp_player.remainingTime = QUIZ_TIME_FOR_ANSWER;
  goog.dom.getElement('timer_value').textContent = qp_player.remainingTime;

  qp_player.specialEffects(correct);
  qp_player.currentQuestion++;
  goog.Timer.callOnce(qp_player.showNextQuestion, 500);
};

/**
 * Submits whole quiz at the end.
 */
qp_player.submitQuiz = function() {
  goog.dom.getElement('submit_answer').value = 'Submitting ...';
  qp_player.quiz['score'] = qp_player.score;
  goog.net.XhrIo.send('/qp_api/quiz/submit', qp_player.showLeaderboard, 'POST',
    goog.json.serialize(qp_player.quiz));
};

/**
 * Checks if current question has changed.
 */
qp_player.checkStatus = function() {
  goog.net.XhrIo.send('/qp_api/poll/status/' + qp_player.poll['document_id'] +
    '/' + qp_player.poll['internal_data_sheet'], function(e) {
    var currentQuestion = parseInt(e.target.getResponseJson()) - 1;
    var status = goog.dom.getElement('status');
    var question = goog.dom.getElement('question');
    if (currentQuestion != qp_player.poll['current_question']) {
      qp_player.poll['current_question'] = currentQuestion;
      if (currentQuestion == POLLING_STATUS_CLOSED) {
        status.textContent = 'Polling is closed';
        qp_player.show(status);
        qp_player.hide(question);
        qp_player.stopTimer();
      } else if (currentQuestion == POLLING_STATUS_WAITING) {
        qp_player.wait();
      } else {
        qp_player.hide(status);
        var current = qp_player.poll['current_question'];
        var question = qp_player.poll['questions'][current];
        qp_player.showQuestion(question);
      }
    }
  });
};

/**
 * Shows Waiting for instructor UI.
 */
qp_player.wait = function() {
  var status = goog.dom.getElement('status');
  var question = goog.dom.getElement('question');
  status.textContent = 'Waiting for instructor ...';
  qp_player.show(status);
  qp_player.hide(question);
};

/**
 * Starts periodic checks for change in current question.
 */
qp_player.startTimer = function() {
  if (qp_player.timer) {
    qp_player.timer.dispose();
  }
  qp_player.timer = new goog.Timer(POLLING_CHECKING_INTERVAL);
  qp_player.timer.start();
  goog.events.listen(qp_player.timer, goog.Timer.TICK, qp_player.checkStatus);
};

/**
 * Stops periodic checks for change in current question.
 */
qp_player.stopTimer = function() {
  if (qp_player.timer) {
    qp_player.timer.stop();
  }
};

/**
 * Shows previously hidden element.
 *
 * @param {object} element DOM object.
 */
qp_player.show = function(element) {
  goog.style.setStyle(element, 'display', 'block');
};

/**
 * Hides some element from DOM.
 *
 * @param {object} element DOM object.
 */
qp_player.hide = function(element) {
  goog.style.setStyle(element, 'display', 'none');
};

/**
 * Reloads quiz.
 */
qp_player.reload = function() {
  window.location.reload();
};

/**
 * Shows currently active question.
 *
 * @param {Array.<Object>} question All info about question and answers.
 */
qp_player.showQuestion = function(question) {
  goog.dom.getElement('question_text').textContent = question['question_text'];
  goog.dom.getElement('submit_answer').value = 'Submit';
  var answers = question['answers'];
  // Figure out if question is multi-choice or not
  var multiChoice = true;
  var correctAnswers = 0;
  for (var i = 0, answer; answer = answers[i]; i++) {
    if (answer['correct']) {
      correctAnswers++;
    }
  }
  if (correctAnswers == 1) {
    multiChoice = false;
  }
  var hint = goog.dom.getElement('hint');
  if (multiChoice) {
    qp_player.show(hint);
  } else {
    qp_player.hide(hint);
  }
  // Generate UI for answers
  var answersDiv = goog.dom.getElement('answers');
  goog.dom.removeChildren(answersDiv);
  for (var i = 0, answer; answer = answers[i]; i++) {
    var choiceType;
    if (multiChoice) {
      choiceType = 'checkbox';
    } else {
      choiceType = 'radio';
    }
    var choiceButton = goog.dom.createDom('input', {
        'type' : choiceType,
        'name' : 'answer',
        'id' : 'answer_' + i
    });
    var label = goog.dom.createDom('label', {
      'for' : 'answer_' + i
    }, answer['answer_text']);
    var answerDiv = goog.dom.createDom('div', {
      'class' : 'answer'
    });
    goog.dom.appendChild(answerDiv, choiceButton);
    goog.dom.appendChild(answerDiv, label);
    goog.dom.appendChild(answersDiv, answerDiv);
  }
  var questionDiv = goog.dom.getElement('question');
  qp_player.show(questionDiv);
  qp_player.submitted = false;
};

/**
 * Handles response submission.
 */
qp_player.submitPollAnswer = function() {
  if (!qp_player.submitted) {
    goog.dom.getElement('submit_answer').value = 'Submitting ...';
    var currentQuestion = qp_player.poll['current_question'];
    var question = qp_player.poll['questions'][currentQuestion];
    var correct = qp_player.isAnswerCorrect(question);
    qp_player.specialEffects(correct);

    var current = qp_player.poll['current_question'];
    var question = qp_player.poll['questions'][current];
    var answers = [];
    for (var i = 0, answer; answer = question['answers'][i]; i++) {
      if (answer['answered']) {
        answers.push(i + 1);
      }
    }
    var answerText = answers.join(',');
    var response = {
        'anonymous' : question['anonymous'],
        'success' : correct,
        'question_number' : (current + 1),
        'document_id' : qp_player.poll['document_id'],
        'sheet_id' : qp_player.poll['responses_sheet'],
        'answers' : answerText
    };
    goog.net.XhrIo.send('/qp_api/poll/submit', function(e) {
      goog.dom.classes.set(goog.dom.getElement('answers'), '');
      qp_player.wait();
      qp_player.submitted = true;
    }, 'POST', goog.json.serialize(response));
  }
};
/**
 * Changes background color if correct/incorrect.
 *
 * @param {boolean} correct Whether answer was correct or not.
 */
qp_player.specialEffects = function(correct) {
  var answers = goog.dom.getElement('answers');
  if (correct) {
    goog.dom.classes.set(answers, 'correct');
  } else {
    goog.dom.classes.set(answers, 'incorrect');
  }
};

/**
 * Determines if submitted answer is correct or not
 *
 * @param {Array.<Object>} question All info about question and answers.
 * @return {boolean} Whether answer is correct.
 */
qp_player.isAnswerCorrect = function(question) {
  var answersDiv = goog.dom.getElement('answers');
  var children = answersDiv.childNodes;
  var correct = true;
  for (var i = 0, child; child = children[i]; i++) {
    // Checkbox/Radio is first child of the answer div
    var answered = child.childNodes[0].checked;
    var corrAns = question['answers'][i]['correct'];
    if (answered != corrAns) {
      correct = false;
    }
    question['answers'][i]['answered'] = answered;
  }
  question['success'] = correct;
  return correct;
};
