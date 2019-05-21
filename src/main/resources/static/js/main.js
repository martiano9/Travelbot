'use strict';

var chatPage = document.querySelector('#chat-page');
var usernameForm = document.querySelector('#usernameForm');
var messageForm = document.querySelector('#form');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#message-box');
var send = document.querySelector('#send');
var textarea = document.querySelector('#textarea');
var speakButton = document.querySelector('#speak');
var speakEnabled = false;
var connectingElement = document.querySelector('.connecting');

// Web Speech API
var note = '';
var SpeechRecognition = SpeechRecognition || webkitSpeechRecognition;
var recognition = new SpeechRecognition();
recognition.onstart = function() {
    // connectingElement.textContent = 'Voice recognition activated. Try speaking into the microphone.';
    note = '';
};

recognition.onend = function() {

    speakButton.classList.remove('enabled');
    speakButton.classList.add('disabled');
    speakEnabled = false;
    recognition.stop();
    send.click();
    // connectingElement.textContent = 'You were quiet for a while so voice recognition turned itself off.';
};

recognition.onerror = function(event) {
    console.log(event);
    if(event.error == 'no-speech') {
        connectingElement.textContent = 'No speech was detected. Try again.';
    }
};

recognition.onresult = function(event) {

    // event is a SpeechRecognitionEvent object.
    // It holds all the lines we have captured so far.
    // We only need the current one.
    var current = event.resultIndex;

    // Get a transcript of what was said.
    var transcript = event.results[current][0].transcript;

    // Add the current transcript to the contents of our Note.
    note += transcript;
    console.log(note);
    textarea.value = note;
};

// STOMP socket API
var stompClient = null;

/**
 * Initiate connection with server
 * @param event
 */
function connect(event) {
    var socket = new SockJS('/connect');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, onConnected, onError);
}


function onConnected() {
    // Subscribe to the Public Topic
    stompClient.subscribe('/private/chat', onMessageReceived);

    // Tell your username to the server
    stompClient.send("/app/greeting",
        {},
        JSON.stringify({})
    );

    connectingElement.classList.add('hidden');
}


function onError(error) {
    connectingElement.textContent = 'Could not connect to WebSocket server.\nPlease refresh this page to try again!';
    connectingElement.style.color = 'red';
}


function sendMessage(event) {
    var messageContent = textarea.value.trim();
    if(messageContent && stompClient) {
        stompClient.send("/app/chat", {}, messageContent);
        textarea.value = null;
        send.classList.add('hidden');
    }
    event.preventDefault();
}


function onMessageReceived(payload) {
    console.log(payload);
    var message = JSON.parse(payload.body);

    var messageElement = document.createElement('li');
    messageElement.classList.add('no-list');
    messageElement.classList.add('font-arial');

    if(message.type === 'REPLY') {
        messageElement.classList.add('bot-chat-component');
    } else if (message.type === 'QUERY') {
        messageElement.classList.add('user-chat-component');
    }

    var textElement = document.createElement('p');
    var messageText = document.createTextNode(message.content);
    textElement.appendChild(messageText);

    messageElement.appendChild(textElement);

    messageArea.appendChild(messageElement);
    messageArea.scrollTop = messageArea.scrollHeight;
}

speakButton.addEventListener('click', function () {
    if (speakEnabled) {
        speakButton.classList.remove('enabled');
        speakButton.classList.add('disabled');
        speakEnabled = false;
        recognition.stop();
    } else {
        speakButton.classList.add('enabled');
        speakButton.classList.remove('disabled');
        speakEnabled = true;
        note = '';
        recognition.start();
    }
});

messageForm.addEventListener('submit', sendMessage, true)
document.addEventListener('load', connect, true);
textarea.addEventListener('input', function() {
    if (this.value.length) {
        send.classList.remove('hidden');
    }else{
        send.classList.add('hidden');
    }
}, false);

textarea.addEventListener('keypress', function(e) {
    if(e.which === 13){
        // submit via ajax or
        send.click();
    }
}, false);