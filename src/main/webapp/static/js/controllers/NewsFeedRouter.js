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
		
		// Tell Google Analytics about the new page.
		ga('send', 'pageview');
	},
	
	getAllPosts: function() {
		AppController.selectFeed.call(AppController, null);
		
		// Tell Google Analytics about the new page.
		ga('send', 'pageview');
	},
	
	goHome: function() {
		AppController.clearPosts.call(AppController);
		
		// Tell Google Analytics about the new page.
		ga('send', 'pageview');
	}
});

});