(function(){

NewsFeedController = Backbone.View.extend({
	el: $("div .middle"),
	
	events: {
		"click #showAllPosts": "toggleAllPosts",
		"click #showUnreadPosts": "toggleUnreadPosts",
		"click #addNewFeedLink": "addNewFeed",
		"click #removeFeedLink": "removeFeed",
		"click #markAllReadLink": "markAllRead",
		"click #importFeedsLink": "showImportWindow",
		
		// Prevents full page refresh when using pushState.
		// Oddly, Chrome does this but Safari doesn't (!), even though both support pushState.
		"click .feedcategory a": "manualNavigate",
		"click .feed a": "manualNavigate",
	},
	
	manualNavigate: function(evt) {
		evt.preventDefault();
		
		var mql = window.matchMedia("(min-width: 961px)");
		if (!mql.matches) {
			// hide all menus on mobile device.
			window.hideAllMenus();
		}
		
		var hash = evt.target.hash;
		if (hash == undefined)
		{
			// we clicked on the unread counter
			var href = $(evt.target).parent().attr("href");
			hash = href.substring(href.indexOf("#"));
		}
		
		// HACK: trick the router into thinking something's changed.
		// Otherwise, clicking on a link we're already on won't
		// reload the page.
		Backbone.history.fragment = "dfldjsfkj";
		AppRouter.navigate(hash, {trigger: true});
	},
	
	restoreFullUi: function() {
		$(".rightcol").css("margin-left", this.listWidth);
		this.showHideMenuOptions();
	},
	
	useMobileUi: function() {
		$(".rightcol").css("margin-left", 0);
		this.showHideMenuOptions();
	},
	
	initialize: function() {
			
		this.enableInfiniteScrolling = false;
		this.clearPosts();
		this.showHideMenuOptions();
		
		// Set up infinite scrolling.
		var self = this;
		self.currentPostCount = 0;
		var win = $(window);
		var doc = $(document);
		var throttledFn = _.throttle(function () {
			if (self.enableInfiniteScrolling && self.articleCollection)
			{
				self.enableInfiniteScrolling = false;
				self.articleCollection.currentPage += 1;
				self.articleCollection.fetch({
					success: function(collection, response, options) {
						if (self.articleCollection.length != self.currentPostCount)
						{
							self.enableInfiniteScrolling = true;
							self.currentPostCount = self.articleCollection.length;
						}
					},
					error: function(x,y,z) { y.url = self.articleCollection.urlBase; self.collectionFetchErrorHandler(x,y,z); },
					reset: false,
					remove: false
				});
			}	
		}, 1000);
		win.scroll(function() { 
			if (win.scrollTop() >= doc.height() - win.height() - 10) {
				throttledFn();
   			}
   		});
		
		// Set up keyboard navigation
		win.keypress(function(e) {
			if (e.keyCode == 74 || e.keyCode == 75 ||
			    e.keyCode == 106 || e.keyCode == 107)
			{
				// Scroll up/down one article.
				if (self.articleCollection && self.articleCollection.length > 0)
				{
					if ((e.keyCode == 75 || e.keyCode == 107) && self.currentArticle > 0)
					{
						// Mark current article as read before proceeding.
						var article = self.articleCollection.at(self.currentArticle);
						article.set("unread", false);
						self.currentArticle = self.currentArticle - 1;
					}
					else if ((e.keyCode == 74 || e.keyCode == 106) && self.currentArticle < self.articleCollection.length - 1)
					{
						// Mark current article as read before proceeding.
						var article = self.articleCollection.at(self.currentArticle);
						article.set("unread", false);
						self.currentArticle = self.currentArticle + 1;
					}
					
					var newArticle = self.articleCollection.at(self.currentArticle);
					var newArticleId = newArticle.attributes.article.id;
					var newArticleOffset = $("a[name='article" + newArticleId + "']").offset();
					$('html, body').animate({
						scrollTop: newArticleOffset.top - $(".top").height()
					}, 500);
					e.preventDefault();
				}
			}
		});
		
		// Get parameters from local storage, if available.
		if (typeof(Storage) !== "undefined")
		{
			if (!localStorage.showOnlyUnread)
			{
				localStorage.showOnlyUnread = true;
			}
			this.showOnlyUnread = localStorage.showOnlyUnread;
			
			// Only update HTML if we're showing all, since the HTML is hardcoded as unread only.
			// Note: why is it making things into strings? :(
			if (this.showOnlyUnread == "false")
			{
				$("#showAllPosts").unwrap();
				$("#showUnreadPosts").wrap("<a />");
			}
			
			// Reset width of feed list from last session.
			var listWidth = $(".leftcol").width;
			if (localStorage.feedListWidth !== "undefined")
			{
				listWidth = 1 * localStorage.feedListWidth; // typecast to int
			}
			if (isNaN(listWidth))
			{
				listWidth = 250;
			}
			this.listWidth = listWidth;
			$(".leftcol").width(listWidth);
			$(".rightcol").css("margin-left", listWidth);
		}
		else
		{
			// Browser doesn't support local storage, default to unread only.
			this.listWidth = $(".leftcol").width;
			if (isNaN(this.listWidth))
			{
				this.listWidth = 250;
			}
			this.showOnlyUnread = true;
		}
		
		// enable hiding of all menus on mobile device.
		$(".feedmenu").click(function() {
			var mql = window.matchMedia("(min-width: 961px)");
			if (!mql.matches) {
				window.hideAllMenus();
			}
		});
		
		this.listenTo(NewsFeeds, 'add', this.addOneFeed);
		this.listenTo(NewsFeeds, 'reset', this.addAllFeeds);
		this.listenTo(NewsFeeds, 'sort', this.addAllFeeds);
		
		// Perform initial fetch from server.
		NewsFeeds.reset(bootstrappedFeeds);
		self.updateFeedCounts();
		NewsFeeds.each(function(x) { 
			self.stopListening(x, 'change:numUnread');
			self.listenTo(x, 'change:numUnread', function() { self.updateFeedCounts(); });
		});
				
		// Update feed counts every five minutes.
		var self = this;
		window.setInterval(function() { self.updateFeeds(); }, 1000 * (60 * 5));
		
		// make the feed list resizable.
		this.makeDraggable();
	},
	
	showImportWindow: function() {
		this.UploadForm = new FileUploadForm();
		this.UploadForm.show();
	},
	
	// We're not using JQuery's because of the weird way we've set up the CSS. 
	makeDraggable: function() {
		var self = this;
		
		var mouseMoveHandler = function(event) {
			event.preventDefault();
			var newWidth = self._startWidth + (event.pageX - self._startX);
			$(".leftcol").width(newWidth);
			$(".rightcol").css("margin-left", newWidth);
			
			if (typeof(Storage) !== "undefined")
			{
				localStorage.feedListWidth = newWidth;
			}
		};
		
		var mouseUpHandler = function(event) {
			$(document).unbind("mousemove", mouseMoveHandler);
			$(document).unbind("mouseup", mouseUpHandler);
		};
		
		var mouseDownHandler = function(event) {
			event.preventDefault();
			self._startX = event.pageX;
			self._startWidth = $(".leftcol").width();
			
			$(document).mousemove(mouseMoveHandler);
			$(document).mouseup(mouseUpHandler);
		};
		
		$(".leftcol").mousedown(mouseDownHandler);
	},
	
	updateFeeds: function() {
		var self = this;
		
		NewsFeeds.fetch({
			success: function(collection, response, options) {
				self.updateFeedCounts();
				NewsFeeds.each(function(x) { 
					self.stopListening(x, 'change:numUnread');
					self.listenTo(x, 'change:numUnread', function() { self.updateFeedCounts(); });
				});
			},
			error: function(x,y,z) {  y.url = NewsFeeds.urlBase; self.collectionFetchErrorHandler(x,y,z); }
		});
	},

	updateFeedCounts: function() {
		// add up all of the unread posts and put under "All".
		var total_unread = NewsFeeds.reduce(function(memo, feed) { return memo + feed.get("numUnread"); }, 0);
		$("#allCount").text(total_unread);
		if (total_unread == 0) {
			$("#allCount").addClass("hide-element");
		} else {
			$("#allCount").removeClass("hide-element");
		}
		
		// also update count in the title
		document.title = document.title.replace(/^\(\d+\)\s*(.+:\s*)?|(.+:\s*)/, "");
		var feedTitle = "";
		if (this.selectedFeed)
		{
			feedTitle = this.selectedFeed.model.get("feed").title + ": ";
		}
		else
		{
			if (this.$("#allFeedEntry").hasClass("selectedfeed"))
			{
				feedTitle = "all feeds: ";
			}
		}
		
		if (total_unread > 0)
		{
			document.title = "(" + total_unread + ") " + feedTitle + document.title;
		}
		else
		{
			document.title = feedTitle + document.title;
		}
	},
		
	selectFeed: function(feed) {
		this.enableInfiniteScrolling = false;
		this.clearPosts();
	
		$(".welcomeblock").addClass("hide-element");
		$("#homeEntry").removeClass("selectedfeed");
		$("#allFeedEntry").removeClass("selectedfeed");
		if (this.selectedFeed) {
			this.selectedFeed.$el.removeClass("selectedfeed");
		}
		this.selectedFeed = feed;
		
		this.currentArticle = 0;
		this.articleCollection = new NewsArticleCollection([]);
		if (feed) {
			this.articleCollection.urlBase = '/feeds/' + feed.model.id + "/posts?unread_only=" + this.showOnlyUnread;
			feed.$el.addClass("selectedfeed");
			
			// set up correct website URL that the feed provided.
			$("#feedsiteurl").attr("href", feed.model.get("feed").link);
		} else {
			this.articleCollection.urlBase = "/posts?unread_only=" + this.showOnlyUnread;
			this.$("#allFeedEntry").addClass("selectedfeed");
		}
		
		this.showHideMenuOptions();
		
		this.listenTo(this.articleCollection, 'add', this.addOneArticle);
		this.listenTo(this.articleCollection, 'reset', this.addAllArticles);
		
		var self = this;
		this.articleCollection.fetch({
			success: function(collection, response, options) {
				self.enableInfiniteScrolling = true;
				self.currentPostCount = collection.length;
			},
			error: function(x,y,z) { y.url = self.articleCollection.urlBase; self.collectionFetchErrorHandler(x,y,z); }
		});
		
		self.updateFeedCounts();
	},
	
	sortFeeds: function() {
		this.addAllFeeds();
	},
	
	markAllRead: function() {
		if (this.articleCollection && this.articleCollection.length > 0)
		{
			var lastPost = this.articleCollection.at(this.articleCollection.length - 1);
			var lastPostDate = new Date(lastPost.get("article").pubDate).getTime() / 1000;
			var url = this.selectedFeed ? "/feeds/" + this.selectedFeed.model.id + "/posts" : "/posts/";
			var self = this;
		
			url = url + "?upTo=" + lastPostDate;
			$.ajax({
				url: url,
				type: "DELETE",
				success: function(result) {
					self.updateFeeds();
					self.selectFeed(self.selectedFeed);
				}
			});
		} 
		else
		{
			this.updateFeeds();
			this.selectFeed(self.selectedFeed);
		}
	},
	
	addOneFeed: function(feed) {
		var newView = new NewsFeedView({model: feed});
		var renderedView = newView.render();
		feed.view = newView;
		$("#allfeeds").append(renderedView.el);
		if (this.selectedFeed && this.selectedFeed.model.id == feed.id)
		{
			renderedView.$el.addClass("selectedfeed");
			this.selectedFeed = newView;
		}
	},
	
	addAllFeeds: function() {
		$("#allfeeds").children().remove();
		NewsFeeds.each(this.addOneFeed, this);
	},
	
	addOneArticle: function(feed) {
		var newView = new NewsArticleView({model: feed});
		this.$("#postlist").append(newView.render().el);
	},
	
	addAllArticles: function() {
		this.$("#postlist").children().remove();
		this.articleCollection.each(this.addOneArticle, this);
	},
	
	clearPosts: function() {
		this.enableInfiniteScrolling = false;
		
		if (this.articleCollection) {
			// clear event handlers
			this.articleCollection.stopListening();
			this.articleCollection.reset();
			this.articleCollection = null;
			
			// remove posts
			this.$("#postlist").empty();
			self.currentPostCount = 0;
		}
		
		// set selected to Home
		$(".selectedfeed").removeClass("selectedfeed");
		$("#allFeedEntry").removeClass("selectedfeed");
		$("#homeEntry").addClass("selectedfeed");
		$(".welcomeblock").removeClass("hide-element");
		
		this.selectedFeed = null;
		this.showHideMenuOptions();
		this.updateFeedCounts();
	},
	
	showHideMenuOptions: function() {
		//$(".authonly").addClass("hide-element");
		$(".feedonly").addClass("hide-element");
		$(".allonly").addClass("hide-element");
		
		if (this.selectedFeed) {
			$(".feedonly").removeClass("hide-element");
		} else {
			if ($("#allFeedEntry").hasClass("selectedfeed")) {
				$(".allonly").removeClass("hide-element");
			}
		}
	},
	
	toggleAllPosts: function() {
		$("#showAllPosts").unwrap();
		$("#showUnreadPosts").wrap("<a />");
		
		this.showOnlyUnread = false;
		if (typeof(Storage) !== "undefined")
		{
			localStorage.showOnlyUnread = this.showOnlyUnread;
		}
		this.selectFeed(this.selectedFeed);
	},
	
	toggleUnreadPosts: function() {
		$("#showUnreadPosts").unwrap();
		$("#showAllPosts").wrap("<a />");
		
		this.showOnlyUnread = true;
		if (typeof(Storage) !== "undefined")
		{
			localStorage.showOnlyUnread = this.showOnlyUnread;
		}
		this.selectFeed(this.selectedFeed);
	},
	
	addNewFeed: function() {
		// TODO: we probably want something a lot nicer than JS prompt().
		var feedUrl = prompt("Enter the URL of the feed or website that you wish to subscribe to.");
		
		if (feedUrl) {
			var self = this;
			NewsFeeds.addFeed(feedUrl, function(feed) {
				self.updateFeedCounts();
				
				// Navigate directly to the new feed.
				AppRouter.navigate("feeds/" + feed.id, {trigger: true});
			});
		}
	},
	
	addBulkFeeds: function(feeds) {
	    var index = 0;
	    var self = this;
		var feedFn = function() {
			if (index < feeds.length)
			{
				$("#currentImportingFeed").text(index);
				
				var url = feeds[index];
				var nextFn = function() { index++;  self.updateFeedCounts(); feedFn(); };
				NewsFeeds.addFeed(url, nextFn, nextFn);
			}
			else
			{
				$("#currentImportingFeed").text(index + 1);
				$("#importFeedsLink").show();
				$("#importProgress").addClass("hide-element");
				
				noty({ text: "Import complete.", layout: "topRight", timeout: 2000, dismissQueue: true, type: "success" });
			}
		};
		
		if (feeds.length > 0)
		{
			$("#totalImportCount").text(feeds.length);
			$("#importFeedsLink").hide();
			$("#importProgress").removeClass("hide-element");
			feedFn();
		}
	},
	
	removeFeed: function() {
		// TODO: we probably want something a lot nicer than JS confirm()
		var self = this;
		var confirmed = confirm("Are you sure you want to unsubscribe from this feed/website?");
		if (confirmed) {
			var feed = this.selectedFeed;
			feed.model.destroy({success: function() {
				NewsFeeds.remove(feed.model);
				feed.$el.remove();
				self.updateFeedCounts();
				AppRouter.navigate("", {trigger: true});
			}});
		}
	},
	
	globalAjaxErrorHandler: function(xhr, status, errorThrown) {
		this.showErrorBasedOnResponse(xhr);
	},
	
	collectionFetchErrorHandler: function(collection, response, options) {
		// Always GET, but response object does not provide it.
		response.type = "GET";
		this.showErrorBasedOnResponse(response);
	},
	
	showErrorBasedOnResponse: function(xhr) {
		var errorText = "Communications error with the server. Please try again.";
		var url = xhr.url;
		var type = xhr.type.toUpperCase();
		
		// Custom errors depending on the situation.
		if (xhr.status == 422) {
			errorText = "Invalid input. Please verify and try again.";
		} else if (xhr.status == 401) {
			// Session expired, force a relogin.
			location.refresh();
		} else if (/\/feeds\/?/.test(url) && type == "POST") {
			errorText = "Could not add feed. Please try again.";
		} else if (/\/feeds\/\d+\/?/.test(url) && type == "DELETE") {
			errorText = "Could not delete feed. Please try again.";
		} else if (/\/feeds\/\d+\/posts\/?/.test(url) && type == "GET") {
			errorText = "Could not retrieve posts. Please try again.";
		} else if (/\/feeds\/\d+\/posts\/?/.test(url) && type == "DELETE") {
			errorText = "Could not mark posts as read. Please try again.";
		} else if (/\/feeds\/\d+\/posts\/\d+\/?/.test(url) && type == "DELETE") {
			errorText = "Could not mark post as read. Please try again.";
		} else if (/\/feeds\/\d+\/posts\/\d+\/?/.test(url) && type == "PUT") {
			errorText = "Could not mark post as read. Please try again.";
		} else if (/\/posts\/?/.test(url) && type == "GET") {
			errorText = "Could not retrieve posts. Please try again.";
		} else if (/\/posts\/?/.test(url) && type == "DELETE") {
			errorText = "Could not mark posts as read. Please try again.";
		} else if (/\/posts\/\d+\/?/.test(url) && type == "DELETE") {
			errorText = "Could not mark post as read. Please try again.";
		} else if (/\/posts\/\d+\/?/.test(url) && type == "PUT") {
			errorText = "Could not mark post as read. Please try again.";
		} 
		
		noty({ text: errorText, layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" });
	}
});

}).call(this);