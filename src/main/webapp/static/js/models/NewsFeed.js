(function(){

NewsFeedModel = Backbone.Model.extend({
	parse: function(response, options) {
		response.id = response.feed.id;
		return response;
	},
	
	urlRoot: "/feeds",
	
	subtractUnread: function() {
		if (this.get("numUnread") > 0)
		{
			this.set("numUnread", this.get("numUnread") - 1);
			NewsFeeds.sort();
		}
	},
	
	addUnread: function() {
		this.set("numUnread", this.get("numUnread") + 1);
		NewsFeeds.sort();
	}
});

}).call(this);