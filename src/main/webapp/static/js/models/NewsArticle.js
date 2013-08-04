(function(){

NewsArticleModel = Backbone.Model.extend({
	events: {
		"change:unread": "onUnreadStatusChanged"
	},
	
	markRead: function() {
		this.set("unread", false);
	},
	
	markUnead: function() {
		this.set("unread", true);
	},
	
	onUnreadStatusChanged: function() {
		// We cannot rely on normal Backbone.sync() to update unread status
		// due to the unusual REST API calls needed. Perform the jQuery AJAX
		// call by hand here.
		var httpType = "PUT";
		if (this.get("unread") == false)
		{
			httpType = "DELETE";
		}
		$.ajax("/feeds/" + this.get("article").feedId + "/posts/" + this.get("article").id, {
			type: httpType,
			error: function(xhr, status, errorThrown) {
				// TODO
			}
		});
	}
});

}).call(this);