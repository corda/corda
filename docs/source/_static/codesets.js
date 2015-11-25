$(document).ready(function() {
    $(".codeset").each(function(index, el) {
        var c = $("<div class='codesnippet-widgets'><span class='current'>Kotlin</span><span>Java</span></div>");
        var kotlinButton = c.children()[0];
        var javaButton = c.children()[1];
        kotlinButton.onclick = function() {
            $(el).children(".highlight-java")[0].style.display = "none";
            $(el).children(".highlight-kotlin")[0].style.display = "block";
            javaButton.setAttribute("class", "");
            kotlinButton.setAttribute("class", "current");
        };
        javaButton.onclick = function() {
            $(el).children(".highlight-java")[0].style.display = "block";
            $(el).children(".highlight-kotlin")[0].style.display = "none";
            kotlinButton.setAttribute("class", "");
            javaButton.setAttribute("class", "current");
        };
        c.insertBefore(el);
    });
});