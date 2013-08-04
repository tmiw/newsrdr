(function(){

var NewsFeedCollection = Backbone.Collection.extend({
	model: NewsFeedModel,
	
	url: '/feeds',
	
	parse: function(response) {
		return response.data;
	}
});

NewsFeeds = new NewsFeedCollection;
}).call(this);