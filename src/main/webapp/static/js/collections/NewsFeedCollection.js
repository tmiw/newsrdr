(function(){

var NewsFeedCollection = Backbone.Collection.extend({
	model: NewsFeedModel,
	
	url: '/feeds',
	
	comparator: function(obj1, obj2) {
		var diff = -(obj1.get("numUnread") - obj2.get("numUnread"));
		if (diff == 0) {
			var title1 = obj1.get("feed").title.toLowerCase();
			var title2 = obj2.get("feed").title.toLowerCase();
			if (title1 < title2) diff = -1;
			else if (title1 > title2) diff = 1;
			else diff = 0;
		} 
		return diff;
	},
	
	parse: function(response) {
		return response.data;
	},
	
	addFeed: function(feedUrl, onSuccessFn, onFailFn) {
		// shoehorn an AJAX request to add a feed.
		var self = this;
		$.ajax({
			data: {
				url: feedUrl
			},
			url: "/feeds/",
			type: "POST",
			beforeSend: function() {
				$("#loading").removeClass("hide-element");
			}
		}).done(function(data, textStatus, xhr) {
			data.id = data.feed.id;
			feed = new NewsFeedModel(data);
			self.add(feed);
			
			if (onSuccessFn)
			{
				onSuccessFn(feed);
			}
		}).always(function() {
			$("#loading").addClass("hide-element");
		}).fail(function(x, y, z) {
			// We need the HTTP status code as well but this does not provide it.
			this.status = x.status;
			if (onFailFn) {
				onFailFn(this);
			} else {
				AppController.globalAjaxErrorHandler(this, y, z);
			}
		});
	}
});

NewsFeeds = new NewsFeedCollection;
}).call(this);