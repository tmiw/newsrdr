(function(){

NewsArticleCollection = Backbone.Collection.extend({
	model: NewsArticleModel,

	parse: function(response) {
		return response;
	},
	
	url: function() {
		if (this.length == 0)
		{
			var today = new Date();
			// format: yyyy-MM-dd'T'HH:mm:ss'Z'
			this.latest_post_date =
				today.getUTCFullYear() + "-" + (today.getUTCMonth() + 1) + "-" + today.getUTCDate() + "T" +
				today.getUTCHours() + ":" + today.getUTCMinutes() + ":" + today.getUTCSeconds() + "Z";
		}
		var latest_post = "&latest_post_date=" + this.latest_post_date;
		return this.urlBase + "&page=" + this.currentPage + latest_post;
	},
	
	currentPage: 0
});

}).call(this);