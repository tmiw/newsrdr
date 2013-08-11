(function(){

NewsArticleCollection = Backbone.Collection.extend({
	model: NewsArticleModel,

	parse: function(response) {
		return response;
	},
	
	url: function() {
		var latest_post = "";
		if (this.length > 0)
		{
			latest_post = "&latest_post_date=" + this.at(0).get("article").pubDate;
		}
		return this.urlBase + "&page=" + this.currentPage + latest_post;
	},
	
	currentPage: 0
});

}).call(this);