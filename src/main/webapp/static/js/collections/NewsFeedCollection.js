(function(){

var NewsFeedCollection = Backbone.Collection.extend({
	model: NewsFeedModel,
	
	url: '/feeds',
	
	comparator: function(obj1, obj2) {
		var diff = -(obj1.get("numUnread") - obj2.get("numUnread"));
		if (diff == 0) {
			var title1 = obj1.get("feed").title;
			var title2 = obj2.get("feed").title;
			for (var i = 0; i < (title1.length < title2.length ? title1.length : title2.lengt); i++)
			{
				diff = title1.charCodeAt(i) - title2.charCodeAt(i);
				if (diff != 0) break;
			}
			if (diff == 0) {
				diff = title1.length - title2.length;
			}
		} 
		return diff;
	},
	
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