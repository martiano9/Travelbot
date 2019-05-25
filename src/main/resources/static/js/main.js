'use strict';

var chatPage = document.querySelector('#chat-page');
var usernameForm = document.querySelector('#usernameForm');
var messageForm = document.querySelector('#form');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#message-box');
var logArea = document.querySelector('#log-box');
var send = document.querySelector('#send');
var textarea = document.querySelector('#textarea');
var speakButton = document.querySelector('#speak');
var connectingElement = document.querySelector('.connecting');

/**
 *  ==================================================
 *  Google Map API
 *  ==================================================
 */
var pos = {};
if (navigator.geolocation) {
    pos = navigator.geolocation.getCurrentPosition(function (position) {
        pos = position;
    });
}

var map;
function initMap() {
    map = new google.maps.Map(document.getElementById('map'), {
        center: {lat: -34.397, lng: 150.644},
        zoom: 8
    });
}

/**
 *  ==================================================
 *  Speech to text API
 *  ==================================================
 */
var note = '';
var speakEnabled = false;
var SpeechRecognition = SpeechRecognition || webkitSpeechRecognition;
var recognition = new SpeechRecognition();
recognition.continuous = true;
recognition.interimResults = false;

recognition.onstart = function() {
    toggleSpeak(true);
    // connectingElement.textContent = 'Voice recognition activated. Try speaking into the microphone.';
    note = '';
};

recognition.onend = function() {
    toggleSpeak(false);
    send.click();
    // connectingElement.textContent = 'You were quiet for a while so voice recognition turned itself off.';
};

recognition.onspeechend = function (ev) {
    console.log('onspeechend');
    send.click();
    note = '';
};

recognition.onerror = function(event) {
    console.log('onerror');
    console.log(event);
    if(event.error == 'no-speech') {
        connectingElement.textContent = 'No speech was detected. Try again.';
    }
};

recognition.onresult = function(event) {
    console.log('onresult');
    // event is a SpeechRecognitionEvent object.
    // It holds all the lines we have captured so far.
    // We only need the current one.
    var current = event.resultIndex;

    // Get a transcript of what was said.
    var transcript = event.results[current][0].transcript;

    var interim_transcript = '';
    for (var i = event.resultIndex; i < event.results.length; ++i) {
        if (event.results[i].isFinal) {
            // finalize and show the completed text
            note += event.results[i][0].transcript;
            send.click();
        } else {
            // run the speech and output it
            interim_transcript += event.results[i][0].transcript;
        }
    }

    textarea.value = note;
    send.click();
    note = '';
    console.log('update text');

};

function startSpeaking() {
    toggleSpeak(true);
    note = '';
    recognition.start();
}

function stopSpeaking() {
    toggleSpeak(false);
    recognition.stop();
}

function toggleSpeak(foo) {
    if (foo) {
        speakButton.classList.add('enabled');
        speakButton.classList.remove('disabled');
    } else {
        speakButton.classList.remove('enabled');
        speakButton.classList.add('disabled');
    }
    speakEnabled = foo;
}


/**
 *  ==================================================
 *  Text to speech API
 *  ==================================================
 */
var voices = window.speechSynthesis.getVoices();

function textToSpeech(txt) {
    if (speakEnabled) {
        var msg = new SpeechSynthesisUtterance(txt);

        // Txt to speech config
        msg.lang = 'en-US';
        msg.pitch = 1;
        msg.rate = 1;
        msg.voice = voices[9];
        msg.voiceURI = 'native';
        msg.volume = 1;

        //if the computer is responding, the system stops recognising speech.
        msg.onstart = function(){
            recognition.stop();
        };

        //if the computer finishes responding, the system restarts recognising speech.
        msg.onend = function(event) {
            recognition.start();
        };

        speechSynthesis.speak(msg);
    }
}

// STOMP socket API
var stompClient = null;
var connected = false;
var greeted = false;
/**
 * Initiate connection with server
 * @param event
 */
function connect(event) {
    if (!connected) {
        var socket = new SockJS('/connect');
        stompClient = Stomp.over(socket);

        stompClient.connect({}, onConnected, onError);
        connected = true;
    }

}


function onConnected() {
    if (!greeted) {
        // Subscribe to the Public Topic
        stompClient.subscribe('/private/chat', onMessageReceived);
        stompClient.subscribe('/private/log', onLogged);

        // Tell your username to the server
        stompClient.send("/app/greeting",
            {},
            JSON.stringify({})
        );

        connectingElement.classList.add('hidden');
        greeted = true;
    }
}

function onLogged(payload) {
    var log = JSON.parse(payload.body);
    var messageElement = document.createElement('li');
    messageElement.classList.add('no-list');
    messageElement.classList.add('font-arial');

    var textElement = document.createElement('p');
    var messageText = document.createTextNode(log.content);
    textElement.appendChild(messageText);

    messageElement.appendChild(textElement);

    logArea.appendChild(messageElement);
    logArea.scrollTop = logArea.scrollHeight;
}


function onError(error) {
    connectingElement.textContent = 'Could not connect to WebSocket server.\nPlease refresh this page to try again!';
    connectingElement.style.color = 'red';
}


function sendMessage(event) {
    var messageContent = textarea.value.trim();
    if(messageContent && stompClient) {
        stompClient.send("/app/chat", pos, messageContent);
        textarea.value = null;
        send.classList.add('hidden');
    }
    event.preventDefault();
}


function onMessageReceived(payload) {
    // console.log(payload);
    var message = JSON.parse(payload.body);
    console.log(message.stemmedQuestion);
    var messageElement = document.createElement('li');
    messageElement.classList.add('no-list');
    messageElement.classList.add('font-arial');

    if(message.type === 'REPLY') {
        messageElement.classList.add('bot-chat-component');
        textToSpeech(message.content);
    } else if (message.type === 'QUERY') {
        messageElement.classList.add('user-chat-component');
    }

    var textElement = document.createElement('p');
    var messageText = document.createTextNode(message.content);
    textElement.appendChild(messageText);

    messageElement.appendChild(textElement);

    if (message.extraData === "current-location") {
        var mapElement = document.createElement('div');
        mapElement.setAttribute("id", "map");
        var myCenter = new google.maps.LatLng(pos.coords.latitude, pos.coords.longitude);
        var map = new google.maps.Map(mapElement, {
            center: myCenter,
            zoom: 16
        });
        var marker=new google.maps.Marker({
            position:myCenter
        }); //initialize map, set the center

        marker.setMap(map);
        messageElement.appendChild(mapElement);
    }

    messageArea.appendChild(messageElement);
    messageArea.scrollTop = messageArea.scrollHeight;
}

function appendMap() {

}

speakButton.addEventListener('click', function () {
    if (speakEnabled) {
        stopSpeaking();
    } else {
        startSpeaking();
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