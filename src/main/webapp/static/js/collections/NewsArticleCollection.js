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
			// Excludes the current oldest post.
			var lastDate = (Date.parse(this.models[this.length - 1].attributes.article.pubDate) / 1000) - 1;
			latest_post = "&latest_post_date=" + lastDate;
		}
		return this.urlBase + latest_post;
	},
	
	currentPage: 0
});

}).call(this);