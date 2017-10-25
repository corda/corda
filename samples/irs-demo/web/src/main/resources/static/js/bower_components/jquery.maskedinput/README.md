Masked Input Plugin for jQuery
==============================
[![Build Status](https://travis-ci.org/digitalBush/jquery.maskedinput.png)](https://travis-ci.org/digitalBush/jquery.maskedinput)
Overview
--------
This is a masked input plugin for the jQuery javascript library. It allows a user to more easily enter fixed width input where you would like them to enter the data in a certain format (dates,phone numbers, etc). It has been tested on Internet Explorer, Firefox, Safari, Opera, and Chrome.  A mask is defined by a format made up of mask literals and mask definitions. Any character not in the definitions list below is considered a mask literal. Mask literals will be automatically entered for the user as they type and will not be able to be removed by the user.The following mask definitions are predefined:

* a - Represents an alpha character (A-Z,a-z)
* 9 - Represents a numeric character (0-9)
* \* - Represents an alphanumeric character (A-Z,a-z,0-9)

### Usage
First, include the jQuery and masked input javascript files.

```html
<script src="jquery.js" type="text/javascript"></script>
<script src="jquery.maskedinput.js" type="text/javascript"></script>
```

Next, call the mask function for those items you wish to have masked.

```html
jQuery(function($){
   $("#date").mask("99/99/9999");
   $("#phone").mask("(999) 999-9999");
   $("#tin").mask("99-9999999");
   $("#ssn").mask("999-99-9999");
});
```

Optionally, if you are not satisfied with the underscore ('_') character as a placeholder, you may pass an optional argument to the maskedinput method.

```html
jQuery(function($){
   $("#product").mask("99/99/9999",{placeholder:" "});
});
```

Optionally, if you would like to execute a function once the mask has been completed, you can specify that function as an optional argument to the maskedinput method.

```html
jQuery(function($){
   $("#product").mask("99/99/9999",{completed:function(){alert("You typed the following: "+this.val());}});
});
```

You can now supply your own mask definitions.
```html
jQuery(function($){
   $.mask.definitions['~']='[+-]';
   $("#eyescript").mask("~9.99 ~9.99 999");
});
```

You can have part of your mask be optional. Anything listed after '?' within the mask is considered optional user input. The common example for this is phone number + optional extension.

```html
jQuery(function($){
   $("#phone").mask("(999) 999-9999? x99999");
});
```

If your requirements aren't met by the predefined placeholders, you can always add your own. For example, maybe you need a mask to only allow hexadecimal characters. You can add your own definition for a placeholder, say 'h', like so: `$.mask.definitions['h'] = "[A-Fa-f0-9]";` Then you can use that to mask for something like css colors in hex with a `mask "#hhhhhh"`.

```html
jQuery(function($){
   $("#phone").mask("#hhhhhh");
});
```


By design, this plugin will reject input which doesn't complete the mask. You can bypass this by using a '?' character at the position where you would like to consider input optional. For example, a mask of "(999) 999-9999? x99999" would require only the first 10 digits of a phone number with extension being optional.


Getting the bits
----------------
We generally recommend that you use [bower](http://bower.io) to install jquery.maskedinput plugin.

    $ bower install --save jquery.maskedinput


Setting up your Developer Environment
-------------------------------------
jQuery Masked Input uses [NodeJS](http://www.nodejs.org) and [GruntJS](http://www.gruntjs.com) as it's developer platform and build automation tool.

To get your environment setup correctly, you'll need nodejs version 0.8.25 or greater installed. You'll also need to install the grunt command line tool:

    $ sudo npm install -g grunt-cli

Once node is installed on your system all that you need to do is install the developer dependencies and run the grunt build:

    $ npm install
    $ grunt

All of the tests for jQuery Masked Input are run using the [jasmine](http://jasmine.github.io/) test runner.
