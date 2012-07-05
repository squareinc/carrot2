(function($) {
  var ControllerDesktop = $.pluginhelper.make("controller", function(el, options) {
    // Initialize application state
    state.source = options.defaults.source;
    state.results = options.defaults.results;
    state.view = options.defaults.view;
    state.algorithm = options.defaults.algorithm;

    // Initialize and wire the components
    var $search = null, $clusters = null, $documents = null;

    $search = $(options.search.container).search($.extend({}, options.search, {
      sourceChanged: function(source) {
        state.source = source;
      },
      resultsCountChanged: function(count) {
        state.results = count;
      },
      searchTriggered: function(query) {
        state.query = query;
        $(el).attr("class", "results");
      }
    }));
    $search.search("source", state.source);
    $search.search("results", state.results);

    $clusters = $(options.clusters.container).clusters($.extend({}, options.clusters, {
      viewChanged: function(view) {
        state.view = view;
      },
      algorithmChanged: function(algorithm) {
        state.algorithm = algorithm;
      }
    }));
    $clusters.clusters("view", state.view);
    $clusters.clusters("algorithm", state.algorithm);



//    $documents = $(options.documents.container).documents($.extend({}, options.documents, {
//    }));

    // Show the UI when initialization complete
    $(el).attr("class", "results");
    return;
  });

  // Application state management
  var state = {
    source: "",
    results: "",
    algorithm: "",
    view: "",
    query: "",

    encode: function() {
      var url = [];
      process(null, this);
      return url.join("/");

      function process(property, value) {
        if ($.isFunction(value)) {
          return;
        }

        if ($.isArray(value)) {
          $.each(value, function(i, v) {
            process(property + "[]", v);
          });
        } else if ($.isPlainObject(value)) {
          $.each(value, function(key, v) {
            process((property ? property + "." : "") + key, v);
          });
        } else {
          url.push(property, encodeURIComponent(value));
        }
      }
    },

    decode: function(string) {
      var s = string || window.location.href;
      var split = (s.split("#")[1] || "").split("/");
      var decoded = { };
      outer: for (var i = 0; i < split.length / 2; i++) {
        var path = decodeURIComponent(split[i*2] || "").split(/\./);
        var property = path.shift();
        var target = decoded;
        while (path.length > 0) {
          if (!$.isPlainObject(target)) {
            break outer;
          }
          if (typeof target[property] == 'undefined') {
            target[property] = { };
          }
          target = target[property];
          property = path.shift();
        }

        var val = convert(decodeURIComponent(split[i*2 + 1] || ""));
        if (property.indexOf("[]") > 0) {
          property = property.replace(/[\[\]]/g, "");
          if (typeof target[property] == 'undefined') {
            target[property] = [];
          }
          target[property].push(val);
        } else {
          target[property] = val;
        }
      }
      delete decoded.encode;
      delete decoded.decode;
      delete decoded.push;
      $.extend(this, decoded);

      function convert(val) {
        if (val == "false") {
          return false;
        } else if (val == "true") {
          return true;
        } else {
          return val;
        }
      }
    },

    push: function() {
      var prev = window.location.hash;
      window.location.hash = "#" + this.encode();
      return prev != window.location.hash;
    }
  };
})(jQuery);