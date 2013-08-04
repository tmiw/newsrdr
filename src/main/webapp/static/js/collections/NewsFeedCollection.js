(function(){

var NewsFeedCollection = Backbone.Collection.extend({
	model: NewsFeedModel,
	
	url: '/feeds',
	
	parse: function(response) {
		return response.data;
	},
	
	addFeed: function(feedUrl, onSuccessFn) {
		// shoehorn an AJAX request to add a feed.
		var self = this;
		$.post("/feeds/", {
			url: feedUrl
		}, function(data, textStatus, xhr) {
			data.id = data.feed.id;
			feed = new NewsFeedModel(data);
			self.add(feed);
			
			if (onSuccessFn)
			{
				onSuccessFn();
			}
		}).fail(function() {
			// TODO: make errors show up in a friendlier manner.
			alert("could not add feed.");
		});
	}
});

NewsFeeds = new NewsFeedCollection;
}).call(this);