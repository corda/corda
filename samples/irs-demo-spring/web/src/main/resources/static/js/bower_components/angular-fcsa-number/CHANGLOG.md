# Changelog

### 1.5.3

* Bug Fixes
  * Fixed a bug where `-1.2` was being formatted as `-1-1.2`

### 1.5.2

* Bug Fixes
  * `preventInvalidInput` is correctly working again

### 1.5.1

* Bug Fixes
  * Fixed issue where overriding a global option on a specific input element would override that 
  option for all input elements

### 1.5.0

* Enhancements
  * Can set default options with `fcsaNumberConfigProvider.setDefaultOptions()`

### 1.4.1

* Bug Fixes
  * `prepend` and `append` values are removed on focus

### 1.4

* Enhancements
  * Added `prepend` option
  * Added `append` option

### 1.3

* Enhancements
  * Not adding commas to the decimal portion of the number
  * Allowing commas to be entered by the user. Thanks @jbulat
  * A hyphen only value is marked invalid

### 1.2.1

* Refactorings
 * using `angular.element` instead of `$` and therefore no longer requiring jQuery to be used

### 1.2

* Enhancements
 * added 'preventInvalidInput' option

### 1.1

* Enhancements
 * renamed `decimals` option to `maxDecimals`
 * renamed `digits` option to `maxDigits`
