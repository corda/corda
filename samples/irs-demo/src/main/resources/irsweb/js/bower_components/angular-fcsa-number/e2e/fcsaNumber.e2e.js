(function() {
  describe('fcsaNumber', function() {
    describe('on blur', function() {
      it('adds the thousand separator', function() {
        var input;
        input = element(By.model('amount'));
        input.clear();
        input.sendKeys('1000\t');
        return expect(input.getAttribute('value')).toBe('1,000');
      });
      return it('does not add commas to the decimal portion', function() {
        var input;
        input = element(By.model('amount'));
        input.clear();
        input.sendKeys('1234.5678\t');
        return expect(input.getAttribute('value')).toBe('1,234.5678');
      });
    });
    return it('removes the thousand separators on focus', function() {
      var input;
      input = element(By.model('amount'));
      input.clear();
      input.sendKeys('1000\t');
      input.click();
      return expect(input.getAttribute('value')).toBe('1000');
    });
  });

}).call(this);
