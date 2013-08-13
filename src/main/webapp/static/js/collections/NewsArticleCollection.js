(function(){

NewsArticleCollection = Backbone.Collection.extend({
	model: NewsArticleModel,

	parse: function(response) {
		return response;
	},
	
	url: function() {
		var latest_post = ""
		if (this.length > 0)
		{
			this.latest_post_id = this.at(0).id
			var latest_post = "&latest_post_id=" + this.latest_post_id;
		}
		return this.urlBase + "&page=" + this.currentPage + latest_post;
	},
	
	currentPage: 0
});

}).call(this);