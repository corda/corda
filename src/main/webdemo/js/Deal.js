"use strict"

function test() {
    console.log("TESTING");
}

define([], () => {
    return test;
})
