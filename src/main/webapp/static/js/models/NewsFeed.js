(function(){

NewsFeedModel = Backbone.Model.extend({
	parse: function(response, options) {
		response.id = response.feed.id;
		return response;
	},
	
	urlRoot: "/feeds"
});

}).call(this);