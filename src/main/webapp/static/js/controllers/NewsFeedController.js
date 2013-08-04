(function(){

NewsFeedController = Backbone.View.extend({
	el: $("div .middle"),
	
	initialize: function() {
		// TODO: we'll probably want to use local storage for persisting this
		// at some point.
		this.showOnlyUnread = true;
		
		this.listenTo(NewsFeeds, 'add', this.addOneFeed);
		this.listenTo(NewsFeeds, 'reset', this.addAllFeeds);
		this.listenTo(NewsFeeds, 'all', this.render);
		
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
		this.selectedFeed = feed;
		
		if (this.articleCollection) {
			// clear event handlers
			this.articleCollection.stopListening();
		}
		
		this.articleCollection = new NewsArticleCollection([], {
			url: '/feeds/' + feed.model.id + "/posts?unread_only=" + this.showOnlyUnread
		});
		
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
	}
});

}).call(this);