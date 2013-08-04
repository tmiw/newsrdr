(function(){

NewsFeedController = Backbone.View.extend({
	el: $("div .middle"),
	
	events: {
		"click #allFeedEntry": function() { this.selectFeed(null); },
		"click #homeEntry": "clearPosts",
		"click #showAllPosts": "toggleAllPosts",
		"click #showUnreadPosts": "toggleUnreadPosts"
	},
	
	initialize: function() {
		this.clearPosts();
		this.showHideMenuOptions();
		
		// TODO: we'll probably want to use local storage for persisting this
		// at some point.
		this.showOnlyUnread = true;
		
		this.listenTo(NewsFeeds, 'add', this.addOneFeed);
		this.listenTo(NewsFeeds, 'reset', this.addAllFeeds);
		this.listenTo(NewsFeeds, 'all', this.render);
		
		// Perform initial fetch from server.
		this.updateFeeds();
		
		// Update feed counts every five minutes.
		setInterval(function() { this.updateFeeds(); }, 1000 * (60 * 5));
	},
	
	updateFeeds: function() {
		NewsFeeds.fetch({
			success: function(collection, response, options) {
				// add up all of the unread posts and put under "All".
				var total_unread = collection.reduce(function(memo, feed) { return memo + feed.get("numUnread"); }, 0);
				$("#allCount").text(total_unread);
				if (total_unread == 0) {
					$("#allCount").addClass("hide-element");
				} else {
					$("#allCount").removeClass("hide-element");
				}
			}
		});
	},
	
	selectFeed: function(feed) {
		this.clearPosts();
	
		$("#homeEntry").removeClass("selectedfeed");
		$("#allFeedEntry").removeClass("selectedfeed");
		if (this.selectedFeed) {
			this.selectedFeed.$el.removeClass("selectedfeed");
		}
		this.selectedFeed = feed;
		
		if (feed) {
			this.articleCollection = new NewsArticleCollection([], {
				url: '/feeds/' + feed.model.id + "/posts?unread_only=" + this.showOnlyUnread
			});
			feed.$el.addClass("selectedfeed");
			
			// set up correct website URL that the feed provided.
			$("#feedsiteurl").attr("href", feed.model.get("feed").link);
		} else {
			this.articleCollection = new NewsArticleCollection([], {
				url: '/posts/?unread_only=' + this.showOnlyUnread
			});
			this.$("#allFeedEntry").addClass("selectedfeed");
		}
		
		this.showHideMenuOptions();
		
		this.listenTo(this.articleCollection, 'add', this.addOneArticle);
		this.listenTo(this.articleCollection, 'reset', this.addAllArticles);
		this.listenTo(this.articleCollection, 'all', this.render);
		
		this.articleCollection.fetch();
	},
	
	render: function() {
		// TODO
	},
	
	addOneFeed: function(feed) {
		var newView = new NewsFeedView({model: feed});
		this.$("#allfeeds").append(newView.render().el);
	},
	
	addAllFeeds: function() {
		NewsFeeds.each(this.addOneFeed, this);
	},
	
	addOneArticle: function(feed) {
		var newView = new NewsArticleView({model: feed});
		this.$("#postlist").append(newView.render().el);
	},
	
	addAllArticles: function() {
		NewsFeeds.each(this.addOneFeed, this);
	},
	
	clearPosts: function() {
		if (this.articleCollection) {
			// clear event handlers
			this.articleCollection.stopListening();
			this.articleCollection = null;
			
			// remove posts
			this.$("#postlist").empty();
		}
		
		// set selected to Home
		$(".selectedfeed").removeClass("selectedfeed");
		$("#homeEntry").addClass("selectedfeed");
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
		$("#showUnreadPosts").wrap("<a href=\"#\" />");
		
		this.showOnlyUnread = false;
		this.selectFeed(this.selectedFeed);
	},
	
	toggleUnreadPosts: function() {
		$("#showUnreadPosts").unwrap();
		$("#showAllPosts").wrap("<a href=\"#\" />");
		
		this.showOnlyUnread = true;
		this.selectFeed(this.selectedFeed);
	}
});

}).call(this);