(function(){

NewsArticleModel = Backbone.Model.extend({
	onUnreadStatusChanged: function() {
		// We cannot rely on normal Backbone.sync() to update unread status
		// due to the unusual REST API calls needed. Perform the jQuery AJAX
		// call by hand here.
		var self = this;
		var httpType = "PUT";
		if (this.get("unread") == false)
		{
			httpType = "DELETE";
		}
		$.ajax("/feeds/" + this.get("article").feedId + "/posts/" + this.get("article").id, {
			type: httpType,
			error: function(x, y, z) { x.type = httpType; AppController.globalAjaxErrorHandler(x, y, z); },
			success: function() {
				if (httpType == "DELETE")
				{
					self.get("feedObj").subtractUnread();
				}
				else
				{
					self.get("feedObj").addUnread();
				}
			},
			beforeSend: function() {
				$("#loading").removeClass("hide-element");
			},
			complete: function() {
				$("#loading").addClass("hide-element");
			}
		});
	},
	
	onSavedStatusChanged: function() {
		// We cannot rely on normal Backbone.sync() to update saved status
		// due to the unusual REST API calls needed. Perform the jQuery AJAX
		// call by hand here.
		var self = this;
		var httpType = "PUT";
		if (this.get("saved") == false)
		{
			httpType = "DELETE";
		}
		$.ajax("/feeds/" + this.get("article").feedId + "/posts/" + this.get("article").id + "/saved", {
			type: httpType,
			error: function(x, y, z) { x.type = httpType; AppController.globalAjaxErrorHandler(x, y, z); },
			beforeSend: function() {
				$("#loading").removeClass("hide-element");
			},
			complete: function() {
				$("#loading").addClass("hide-element");
			}
		});
	},
	
	initialize: function() {
		this.on("change:unread", this.onUnreadStatusChanged);
		this.on("change:saved", this.onSavedStatusChanged);
	},
	
	markRead: function() {
		this.set("unread", false);
	},
	
	markUnead: function() {
		this.set("unread", true);
	},
	
	markSaved: function() {
		this.set("saved", true);
	},
	
	markUnsaved: function() {
		this.set("saved", false);
	}
});

}).call(this);