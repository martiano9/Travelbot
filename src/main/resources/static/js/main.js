'use strict';

var usernamePage = document.querySelector('#username-page');
var chatPage = document.querySelector('#chat-page');
var usernameForm = document.querySelector('#usernameForm');
var messageForm = document.querySelector('#form');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#message-box');
var send = document.querySelector('#send');
var textarea = document.querySelector('#textarea');
var connectingElement = document.querySelector('.connecting');

var stompClient = null;
var username = null;

var colors = [
    '#2196F3', '#32c787', '#00BCD4', '#ff5652',
    '#ffc107', '#ff85af', '#FF9800', '#39bbb0'
];

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
        textarea.value = '';
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