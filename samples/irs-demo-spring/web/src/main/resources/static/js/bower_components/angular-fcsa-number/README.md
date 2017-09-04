# FCSA Number

An Angular directive that validates numbers and adds commas for the thousands separator. 

So when the user enters `1000` into a textbox and tabs out, the value will be formatted to include the thousands separator and display: `1,000`

If an invalid number is entered, the textbox will be invalid with the `fcsaNumber` error.

## Installation

#### With Bower

    bower install angular-fcsa-number

Then reference `angular-fcsa-number/src/fcsaNumber.js` in your project.

#### Manually

Copy `src/fcsaNumber.js` into your project and reference it.

## Quick Start

Add the `fcsa-number` module as a dependency to you Angular app.
 
    angular.module('yourApp', ['fcsa-number']);

Add the `fcsa-number` attribute to textboxes you want to have validated and formatted with thousands separators.

    <input type='text' ng-model='company.employeeCount' fcsa-number />

When an invalid number is entered by the user, the form and control will become invalid and the 'fcsa-number-invalid' class will be added to the text box.

## Options

Without any options passed to it, fcsa-number will validate that the input is a valid number and will also add commas for the thousand separators.


#### max

Validates the number is not above the max number.

    fcsa-number="{ max: 100 }"

 * Valid: 100
 * Invalid: 101

#### min

Validates the number is not below the min number.

    fcsa-number="{ min: -5 }"

 * Valid: -5
 * Invalid: -6

#### maxDecimals

Validates the number does not have more than the specified number of decimals.

    fcsa-number="{ maxDecimals: 2 }"

 * Valid: 1.23
 * Invalid: 1.234

#### maxDigits

Validates the number does not have more than the specified number of digits.

    fcsa-number="{ maxDigits: 2 }"

 * Valid: 76
 * Invalid: 123

#### preventInvalidInput

By default users are allowed to enter invalid characters, and then the textbox is marked invalid. 
If you want to prevent users from entering invalid characters altogether, then use the `preventInvalidInput` option.
If the user presses the 'a' key, the directive will catch it and prevent 'a' from being shown in the textbox.

    fcsa-number="{ preventInvalidInput: true }"

#### prepend

Prepends the specified text before the number.

    fcsa-number="{ prepend: '$' }"

#### append

Appends the specified text after the number.

    fcsa-number="{ append: '%' }"

## Default Options

It's possible to set the options globally too. You do this by calling `fcsaNumberConfigProvider.setDefaultOptions()` 
inside a config function in your app.

Here's a code example that sets the default options:

```javascript
var app = angular.module('yourApp', ['fcsa-number']);
app.config(['fcsaNumberConfigProvider', function(fcsaNumberConfigProvider) {
  fcsaNumberConfigProvider.setDefaultOptions({
    max: 100,
    min: 0
  });
});
```

The default options can be overridden locally by passing in an options object: `fcsa-number="{ max: 10 }"`

## Developing

Grunt is used to compile the CoffeeScript files and run the tests. To get started run the following commands on the command line:

    // installs the required node modules
    npm install

    // installs the required bower components
    bower install

Run the following command to automatically compile and run the unit and end to end tests whenever you make a change to a file.

    grunt karma:unit:start express:dev watch

To just run the protractor tests, you can run the following command.

    grunt express:dev e2e
