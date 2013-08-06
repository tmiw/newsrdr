(function(){

NewsFeedController = Backbone.View.extend({
	el: $("div .middle"),
	
	events: {
		"click #allFeedEntry": function() { this.selectFeed(null); },
		"click #homeEntry": "clearPosts",
		"click #showAllPosts": "toggleAllPosts",
		"click #showUnreadPosts": "toggleUnreadPosts",
		"click #addNewFeedLink": "addNewFeed",
		"click #removeFeedLink": "removeFeed",
		"click #markAllReadLink": "markAllRead"
	},
	
	initialize: function() {
		this.clearPosts();
		this.showHideMenuOptions();
		
		// Set up infinite scrolling.
		this.enableInfiniteScrolling = false;
		var self = this;
		$(window).scroll(function () {
			if ($(window).scrollTop() >= $(document).height() - $(window).height() - 10) {
				if (self.enableInfiniteScrolling && self.articleCollection)
				{
					self.enableInfiniteScrolling = false;
					self.articleCollection.currentPage += 1;
					self.articleCollection.fetch({
						success: function(collection, response, options) {
							self.enableInfiniteScrolling = true;
						}
					});
				}
   			}
		});
		
		// TODO: we'll probably want to use local storage for persisting this
		// at some point.
		this.showOnlyUnread = true;
		
		this.listenTo(NewsFeeds, 'add', this.addOneFeed);
		this.listenTo(NewsFeeds, 'reset', this.addAllFeeds);
		this.listenTo(NewsFeeds, 'all', this.render);
		
		// Perform initial fetch from server.
		this.updateFeeds();
		
		// Update feed counts every five minutes.
		var self = this;
		window.setInterval(function() { self.updateFeeds(); }, 1000 * (60 * 5));
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
			}
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
		document.title = document.title.replace(/^\(\d+\)\s*/, "");
		if (total_unread > 0)
		{
			document.title = "(" + total_unread + ") " + document.title;
		}
	},
		
	selectFeed: function(feed) {
		this.clearPosts();
	
		$("#homeEntry").removeClass("selectedfeed");
		$("#allFeedEntry").removeClass("selectedfeed");
		if (this.selectedFeed) {
			this.selectedFeed.$el.removeClass("selectedfeed");
		}
		this.selectedFeed = feed;
		
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
		this.listenTo(this.articleCollection, 'all', this.render);
		
		var self = this;
		this.articleCollection.fetch({
			success: function(collection, response, options) {
				self.enableInfiniteScrolling = true;
			}
		});
	},
	
	render: function() {
		// TODO
	},
	
	markAllRead: function() {
		this.articleCollection.each(function(article) { 
			if (article.get("unread")) {
				article.set("unread", false);
			}
		}, this);
	},
	
	addOneFeed: function(feed) {
		var newView = new NewsFeedView({model: feed});
		this.$("#allfeeds").append(newView.render().el);
	},
	
	addAllFeeds: function() {
		this.$("#allfeeds").clear();
		NewsFeeds.each(this.addOneFeed, this);
	},
	
	addOneArticle: function(feed) {
		var newView = new NewsArticleView({model: feed});
		this.$("#postlist").append(newView.render().el);
	},
	
	addAllArticles: function() {
		this.$("#postlist").clear();
		this.articleCollection.each(this.addOneArticle, this);
	},
	
	clearPosts: function() {
		this.enableInfiniteScrolling = false;
		
		if (this.articleCollection) {
			// clear event handlers
			this.articleCollection.stopListening();
			this.articleCollection = null;
			
			// remove posts
			this.$("#postlist").empty();
		}
		
		// set selected to Home
		$(".selectedfeed").removeClass("selectedfeed");
		$("#allFeedEntry").removeClass("selectedfeed");
		$("#homeEntry").addClass("selectedfeed");
		
		this.selectedFeed = null;
		this.showHideMenuOptions();
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
		this.selectFeed(this.selectedFeed);
	},
	
	toggleUnreadPosts: function() {
		$("#showUnreadPosts").unwrap();
		$("#showAllPosts").wrap("<a />");
		
		this.showOnlyUnread = true;
		this.selectFeed(this.selectedFeed);
	},
	
	addNewFeed: function() {
		// TODO: we probably want something a lot nicer than JS prompt().
		var feedUrl = prompt("Enter the URL of the feed that you wish to subscribe to.");
		
		if (feedUrl) {
			var self = this;
			NewsFeeds.addFeed(feedUrl, function() {
				self.updateFeedCounts();
			});
		}
	},
	
	removeFeed: function() {
		// TODO: we probably want something a lot nicer than JS confirm()
		var confirmed = confirm("Are you sure you want to unsubscribe from this feed?");
		if (confirmed) {
			var feed = this.selectedFeed;
			feed.$el.remove();
			this.selectFeed(null);
			NewsFeeds.remove(feed.model);
			feed.model.destroy();
			this.updateFeedCounts();
		}
	}
});

}).call(this);