// PLUGIN: Europeana API
(function (Popcorn) {

	if (!Popcorn) {
		return;
	}
	
	var idx = 0;

	function rightClick(event) {
		if (!event) {
			return false;
		}
		if (event.which) {
			return (event.which === 3);
		}
		if (event.button) {
			return (event.button === 2);
		}
	}



  Popcorn.plugin( "europeana" , function( options ) {
    var containerDiv, //containerDiv is 'position: absolute' to position within frame and fix width/height
        contentDiv,  //contentDiv is 'position: relative' for laying out stuff inside it
        _uri,
        _link,
        _image,
        lang,
        _height, _width, _top, _left,
        _padding = options.padding || "5px",
        _border = options.border || "0px",
        popcorn = this,
        paused = false,
        otherWindows, bigWindow, littleWindow, keyPress;
    
	if (!options || !options.id) {
		return {
	      _setup: function() {}
		};
	}
    
    keyPress = function (event) {
      var key = event.keyCode || event.which;
      if (key === 27) { //escape
        littleWindow();
      }
    };
    
    otherWindows = function(event) {
      if (rightClick(event)) {
        return;
      }

      var target = event.target;
      do {
        if (target === containerDiv) {
          return true;
        }
      } while (target = target.parentNode);
      littleWindow();

      return false;
    };

    bigWindow = function (event) {
      if (rightClick(event)) {
        return;
      }

      paused = popcorn.media.paused;
      popcorn.pause();
      contentDiv.removeEventListener('click', bigWindow, false);
      window.addEventListener('click', otherWindows, true);
      
      window.addEventListener('keydown', keyPress, true);

      //most plugins shouldn't use classList, but this is a custom one, so we know it's been prepared
      containerDiv.classList.add('active');

      containerDiv.style.cssText = '';      

      if (_image && _width && 16 / 9 * 0.4 * _image.height / _image.width > 0.8) {
      	containerDiv.style.width = _image.width / _image.height * 0.8 * 9/16 * 100 + '%';
      }

      if (event) {
        if (event.preventDefault) {
          event.preventDefault();
        }
        if (event.stopPropagation) {
          event.stopPropagation();
        }
        event.returnValue = false;
        event.cancelBubble = true;
      }
      return false;
    };

    littleWindow = function (event) {
      if (rightClick(event)) {
        return;
      }

      containerDiv.classList.remove('active');
      containerDiv.style.width = _width;
      if (_top || _left) {
        containerDiv.style.top = _top;
        containerDiv.style.left = _left;
        containerDiv.style.position = 'absolute';
      }
      contentDiv.addEventListener('click', bigWindow, false);

      window.removeEventListener('keydown', keyPress, true);
      window.removeEventListener('click', otherWindows, true);

      if (!paused) {
        popcorn.play();
      }
       
      if (event) {
        if (event.preventDefault) {
          event.preventDefault();
        }
        if (event.stopPropagation) {
          event.stopPropagation();
        }
        event.returnValue = false;
        event.cancelBubble = true;
      }

      return false;
    };

    options.loaded = false;

    // create a new div this way anything in the target div is left intact
    containerDiv = document.createElement( "div" );
    containerDiv.setAttribute('class', 'europeana');
    containerDiv.id = "europeana" + idx;
    if (options.width) {
      containerDiv.style.width = options.width;
    }
    if (options.height) {
      containerDiv.style.height = options.height;
    }
    containerDiv.style.display = "none";
    idx++;
    
    // ensure the target container the user chose exists
    if ( document.getElementById( options.target ) ) {
      document.getElementById( options.target ).appendChild( containerDiv );
    } else {
      throw ( "europeana target container doesn't exist" );
    }

    if (options.top || options.left) {
      containerDiv.style.position = 'absolute';
      containerDiv.style.top = options.top;
      containerDiv.style.left = options.left;
    }
	
    contentDiv = document.createElement( "div" );
    contentDiv.style.position = 'relative';
    containerDiv.appendChild(contentDiv);
    
    //unfortunately, we have to use a local proxy because of cross-origin restrictions
    _uri = 'europeana.php?id=' + options.id;
    
    var req = new XMLHttpRequest();
    req.open('GET', _uri);
    req.onreadystatechange = function(event) {
    	var nsString = '',
			ns = {
				srw: 'http://www.loc.gov/zing/srw/',
				diag: 'http://www.loc.gov/zing/srw/diagnostic/',
				xcql: 'http://www.loc.gov/zing/cql/xcql/',
				mods: 'http://www.loc.gov/mods/v3',
				europeana: 'http://www.europeana.eu',
				enrichment: 'http://www.europeana.eu/schemas/ese/enrichment/',
				dcterms: 'http://purl.org/dc/terms/',
				dc: 'http://purl.org/dc/elements/1.1/',
				dcx: 'http://purl.org/dc/elements/1.1/',
				tel: 'http://krait.kb.nl/coop/tel/handbook/telterms.html',
				xsi: 'http://www.w3.org/2001/XMLSchema-instance'
    		};;
    	
    	function resolver(prefix) {
    		return ns[prefix] || null;
    	}

    	function xpath(path, context) {
    		var doc, result, prefix;
    		doc = context.ownerDocument || context;
    		if (doc.evaluate) {
    			result = doc.evaluate(path, context, resolver, XPathResult.FIRST_ORDERED_NODE_TYPE, null);

    			return result && result.singleNodeValue;
    		} else {
    			//internet explorer
    			doc.setProperty("SelectionLanguage", "XPath");
    			
    			if (!nsString) {
    				nsString = [];
    				for (prefix in ns) {
    					nsString.push('xmlns:' + prefix + '="' + ns[prefix] + '"');
    				}
    				nsString = nsString.join(' ');
					doc.setProperty("SelectionNamespaces", nsString);
    			}
    			
    			if (context === doc && doc.documentElement) {
    				context = doc.documentElement;
    			}
    			
    			return context.selectSingleNode(path);
    		}
    	}
    	
    	function truncate(text, length) {
    		var i, out = [], remaining = length - 3;
    		
    		if (!text) {
    			return '';
    		}
    		
    		if (text.length <= length) {
    			return text;
    		}
    		
    		text = text.split(' ');
    		
    		if (text[0].length > length) {
    			return text[0].substr(0, length - 3) + '...';
    		}
    		
    		for (i = 0; i < text.length; i++) {
    			if (text[i].length <= remaining) {

    				out.push(text[i]);
    			} else {
    				return out.join(' ') + '...';
    			}
    			remaining -= out[i].length + 1;
    		}
    		return out.join(' ');
    	}
    
    	if (req.readyState === 4 && req.status === 200) {
    		var doc = req.responseXML;
    		if (!doc) {
    			return;
    		}
    		var docElement = !doc.ownerDocument ? doc.documentElement : doc.ownerDocument.documentElement;

    		var title = xpath('//dc:title', docElement);
    		if (title) {
    			title = title.firstChild.nodeValue || '';
    			title = truncate(title, 50);
    		} else {
    			title = '';
    		}

    		var description = xpath('//dc:description', docElement);
    		if (description) {
    			description = description.firstChild.nodeValue || '';
    		} else {
    			description = '';
    		}
    		
    		var source = xpath('//dc:source', docElement);
    		if (source) {
    			source = source.firstChild.nodeValue || '';
    		} else {
				source = xpath('//europeana:provider', docElement);
				if (source) {
					source = source.firstChild.nodeValue || '';
				} else {
					source = '';
				}
    		}
    		if (source) {
    			source = 'Europeana/' + source;
    			source = truncate(source, 32);
    		}

    		var imagePath = xpath('//europeana:object', docElement);
    		if (imagePath) {
    			imagePath = imagePath.firstChild.nodeValue || '';
    		} else {
    			imagePath = '';
    		}

			var content = document.createElement('div');
			content.setAttribute('class', 'content');
			var article = document.createElement('article');
			article.appendChild(document.createTextNode(description));
			content.appendChild(article);
			content.appendChild(document.createElement('br'));
			contentDiv.appendChild(content);
			
			var info = document.createElement('div');
			info.setAttribute('class', 'info');
			
			title = title.replace('<', '&lt;');
			title = title.replace('>', '&gt;');
			
			info.innerHTML = '<div class="media"><span class="hiding minimize" style="float: right; margin-right: 20px;">minimize window</span>image<span class="hiding"> | <span class="popcorn-source">source</span>: <a href="http://www.europeana.eu/portal/record/' + options.id + '.html" target="_new">' + source + '</a></span></div><div><a href="http://www.europeana.eu/portal/record/' + options.id + '.html" target="_new">' + title + '</a></div><div class="watch">view details</div>';
			
			var minimize = info.getElementsByClassName('minimize').item(0);
			minimize.addEventListener('click', littleWindow, false);
	
			contentDiv.appendChild(info);
	 
	 		if (imagePath) {
				_image = document.createElement('img');
				_image.src = imagePath;
				_image.onload = function() {
					options.loaded = true;
				};
				content.insertBefore(_image, content.firstChild);
			} else {
				options.loaded = true;
			}

			_width = options.width;
			_top = options.top;
			_left = options.left;
	
			contentDiv.addEventListener('click', bigWindow, false);

    	}
    };
    req.send(null);

    var duration = options.end - options.start;
    if (isNaN(options.fadeIn)) {
      options.fadeIn = Math.min(0.25, duration / 8);
    } else if (options.fadeIn > duration) {
      options.fadeIn = duration;
    }
    duration -= options.fadeIn;
    if (isNaN(options.fadeOut)) {
      options.fadeOut = Math.min(0.25, (options.end - options.start) / 8);
    } else if (options.fadeOut > duration) {
      options.fadeIn = duration;
    }

    return {
      /**
* @member flickr
* The start function will be executed when the currentTime
* of the video reaches the start time provided by the
* options variable
*/
      start: function( event, options ) {
        var isLoaded = function() {
          if ( _image && ( options.loaded || _image.loaded ) ) {
            containerDiv.style.display = "";
          } else {
            setTimeout(isLoaded, 1);
          }
        };
        isLoaded();
      },
      /**
* @member flickr
* The end function will be executed when the currentTime
* of the video reaches the end time provided by the
* options variable
*/
      end: function( event, options ) {
        containerDiv.style.display = "none";
      },
      _teardown: function( options ) {
      	var target = document.getElementById( options.target );
      	if (target) {
      		target.removeChild( containerDiv );
      	}
      },
      frame: function(event, options, time){
      	var t = time - options.start;
      	var opacity = 1;
      	if (t < options.fadeIn) {
      		opacity = t / options.fadeIn;
      	} else if (time > options.end - options.fadeOut) {
      		opacity = (options.end - time) / options.fadeOut;
      	}
        containerDiv.style.opacity = opacity;
      }
    };
  },
  {
    about: {
      name: "Popcorn Flickr Plugin",
      version: "0.2",
      author: "Scott Downe, Steven Weerdenburg, Annasob",
      website: "http://scottdowne.wordpress.com/"
    },
    options: {
      start: {
        elem: "input",
        type: "number",
        label: "In"
      },
      end: {
        elem: "input",
        type: "number",
        label: "Out"
      },
      userid: {
        elem: "input",
        type: "text",
        label: "UserID"
      },
      tags: {
        elem: "input",
        type: "text",
        label: "Tags"
      },
      username: {
        elem: "input",
        type: "text",
        label: "Username"
      },
      apikey: {
        elem: "input",
        type: "text",
        label: "Api_key"
      },
      target: "flickr-container",
      height: {
        elem: "input",
        type: "text",
        label: "Height"
      },
      width: {
        elem: "input",
        type: "text",
        label: "Width"
      },
      padding: {
        elem: "input",
        type: "text",
        label: "Padding"
      },
      border: {
        elem: "input",
        type: "text",
        label: "Border"
      },
      numberofimages: {
        elem: "input",
        type: "text",
        label: "Number of Images"
      }
    }
  });
}( window.Popcorn ));