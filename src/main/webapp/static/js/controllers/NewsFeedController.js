(function(){

NewsFeedController = Backbone.View.extend({
	el: $("div .middle"),
	
	initialize: function() {
		this.listenTo(NewsFeeds, 'add', this.addOneFeed);
		this.listenTo(NewsFeeds, 'reset', this.addAllFeeds);
		this.listenTo(NewsFeeds, 'all', this.render);
		
		NewsFeeds.fetch();
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
	}
});

}).call(this);