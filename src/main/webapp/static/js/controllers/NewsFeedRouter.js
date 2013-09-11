$(function(){

NewsFeedRouter = Backbone.Router.extend({
	routes: {
		"":				"goHome",
		"feeds/:fid":	"navigateToFeed",
		"feeds":		"getAllPosts",
	},
	
	navigateToFeed: function(fid) {
		var feed = NewsFeeds.get(fid);
		if (feed) {
			AppController.selectFeed.call(AppController, feed.view);
		} else {
			this.navigate("home");
		}
		this.updateAnalytics();
	},
	
	getAllPosts: function() {
		AppController.selectFeed.call(AppController, null);
		this.updateAnalytics();
	},
	
	goHome: function() {
		AppController.clearPosts.call(AppController);
		this.updateAnalytics();
	},
	
	updateAnalytics: function() {
		// Tell Google Analytics about the new page.
		ga('send', 'pageview', {'title': document.title, 'page': location.href});
	}
});

});