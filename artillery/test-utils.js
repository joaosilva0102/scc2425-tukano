'use strict';

/***
 * Exported functions to be used in the testing scripts.
 */
module.exports = {
  uploadRandomizedUser,
  processRegisterReply,
    processDeleteUser,
    processShortReply,
    processUpdateUser,
    getRandomUser,
    getRandomShort,
    updateRandomUser,
    randomPattern,
    getRandomShortAndUser,
    getRandomShortAndOwner,
    processDeleteShort

}


const fs = require('fs') // Needed for access to blobs.

let registeredUsers = {} // Dictionary with usernames and user object
let registeredShorts = {} // Dictionary with shorts and short object

// All endpoints starting with the following prefixes will be aggregated in the same for the statistics
let statsPrefix = [ ["/rest/media/","GET"],
			["/rest/media","POST"]
	]

// Function used to compress statistics
global.myProcessEndpoint = function( str, method) {
	let i = 0;
	for( i = 0; i < statsPrefix.length; i++) {
		if( str.startsWith( statsPrefix[i][0]) && method == statsPrefix[i][1])
			return method + ":" + statsPrefix[i][0];
	}
	return method + ":" + str;
}

// Returns a random username constructed from lowercase letters.
function randomUsername(char_limit){
    const letters = 'abcdefghijklmnopqrstuvwxyz';
    let username = '';
    let num_chars = randomNumber(5, char_limit);
    for (let i = 0; i < num_chars; i++) {
        username += letters[Math.floor(Math.random() * letters.length)];
    }
    return username;
}

function randomNumber(min, max) {
    let range = max - min;
    return Math.floor(Math.random() * range) + min;
}

// Returns a random password, drawn from printable ASCII characters
function randomPassword(pass_len){
    const skip_value = 33;
    const lim_values = 94;
    
    let password = '';
    let num_chars = Math.floor(Math.random() * pass_len);
    for (let i = 0; i < pass_len; i++) {
        let chosen_char =  Math.floor(Math.random() * lim_values) + skip_value;
        if (chosen_char == "'" || chosen_char == '"')
            i -= 1;
        else
            password += chosen_char
    }
    return password;
}

function randomPattern(requestParams, context, ee, next) {
    const letters = 'abcdefghijklmnopqrstuvwxyz';
    let pattern = ""
    let patternLenght = randomNumber(0, 2)
    for (let i = 0; i < patternLenght; i++) {
        pattern += letters[randomNumber(0, letters.length)];
    }

    context.vars.pattern = pattern
    return next()
}

/**
 * Register a random user.
 */

function uploadRandomizedUser(requestParams, context, ee, next) {
    let username = randomUsername(10);
    let pword = randomPassword(10);
    let email = username + "@campus.fct.unl.pt";
    let displayName = username;

    const user = {
        userId: username,
        pwd: pword,
        email: email,
        displayName: username
    };
    context.vars.user = user
    requestParams.body = JSON.stringify(user);
    return next();
}

function getRandomUser(requestParams, context, ee, next) {
    let users = Object.keys(registeredUsers)
    const rand = randomNumber(0, users.length)
    const user = registeredUsers[users[rand]]
    let username = user?.userId
    let password = user?.pwd

    context.vars.username = username;
    context.vars.password = password;

    return next();
}

function updateRandomUser(requestParams, context, ee, next) {
    let users = Object.keys(registeredUsers)
    const rand = randomNumber(0, users.length)
    const oldUser = registeredUsers[users[rand]]
    const username = oldUser?.userId
    const password = oldUser?.pwd
    const newPassword = password
    const newEmail = username + "_upd@campus.fct.unl.pt";
    const newDisplayName = username + "_upd"

    const user = {
        userId: username,
        pwd: newPassword,
        email: newEmail,
        displayName: newDisplayName
    }

    context.vars.username = username
    context.vars.password = password
    requestParams.body = JSON.stringify(user);
    return next();
}

function getRandomShort(requestParams, context, ee, next) {
    let shorts = Object.keys(registeredShorts)
    let rand = randomNumber(0, shorts.length)
    const short = registeredShorts[shorts[rand]]
    context.vars.shortId = short?.shortId

    return next();
}

function getShortOwner(requestParams, context, ee, next) {
    const shortId = context.vars.shortId
    let username = registeredShorts[shortId]?.ownerId
    let password = registeredUsers[username]?.password

    context.vars.username = username
    context.vars.password = password
    return next()
}

function getRandomShortAndUser(requestParams, context, ee, next) {
    getRandomShort(requestParams, context, ee, () => {
        getRandomUser(requestParams, context, ee, next)
    })
}

function getRandomShortAndOwner(requestParams, context, ee, next) {
    getRandomShort(requestParams, context, ee, () => {
        getShortOwner(requestParams, context, ee, next)
    })
}

function processUpdateUser(requestParams, response, context, ee, next) {
    if( typeof response.body !== 'undefined' && response.body.length > 0) {
        const username = response.body.username
        registeredUsers[username] = response.body
    }
    return next();
}

function processDeleteUser(requestParams, response, context, ee, next) {
    if( typeof response.body !== 'undefined' && response.body.length > 0) {
        const username = context.vars.username
        delete registeredUsers[username]
    }
    return next();
}

function processDeleteShort(requestParams, response, context, ee, next) {
    if( typeof response.body !== 'undefined' && response.body.length > 0) {
        const shortId = context.vars.shortId
        delete registeredShorts[shortId]
    }
    return next();
}

/**
 * Process reply of the user registration.
 */
function processRegisterReply(requestParams, response, context, ee, next) {
    if( typeof response.body !== 'undefined' && response.body.length > 0) {
        registeredUsers[response.body] = context.vars.user;
    }
    return next();
}

function processShortReply(requestParams, response, context, ee, next) {
    if( typeof response.body !== 'undefined' && response.body.length > 0) {
        const shortId = response.body.shortId
        registeredShorts[shortId] = response.body
    }
    return next();
}

