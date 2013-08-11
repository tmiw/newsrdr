$(function(){

NewsFeedRouter = Backbone.Router.extend({
	routes: {
		"":			"goHome",
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
	},
	
	getAllPosts: function() {
		AppController.selectFeed.call(AppController, null);
	},
	
	goHome: function() {
		AppController.clearPosts.call(AppController);
	}
});

});