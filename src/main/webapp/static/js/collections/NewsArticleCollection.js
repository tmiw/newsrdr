(function(){

NewsArticleCollection = Backbone.Collection.extend({
	model: NewsArticleModel,

	parse: function(response) {
		return response;
	},
	
	url: function() {
		return this.urlBase + "&page=" + this.currentPage;
	},
	
	currentPage: 0
});

}).call(this);