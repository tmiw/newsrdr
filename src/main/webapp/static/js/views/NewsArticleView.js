(function(){

NewsArticleView = Backbone.View.extend({
	tagName: 'li',
	
	className: 'feedpost',
	
	events: {
		"click .markRead": function() { this.model.set("unread", false); },
		"click .markUnread": function() { this.model.set("unread", true); },
		"click .markSaved": function() { this.model.set("saved", true); },
		"click .markUnsaved": function() { this.model.set("saved", false); },
		"click a": function() { this.model.set("unread", false); },
		"click .body": function() { this.model.set("unread", false); },
		"contextmenu .markRead": function() { this.model.set("unread", false); },
		"contextmenu .markUnread": function() { this.model.set("unread", true); },
		"contextmenu a": function() { this.model.set("unread", false); },
		"contextmenu .body": function() { this.model.set("unread", false); },
		"click .sharelink": "toggleShareOptions",
	},
	
	toggleShareOptions: function() {
		var shareTags = this.$('.shareoption');

	  	// Render share links.
	  	if (!shareTags.hasClass("share-buttons-rendered"))
	  	{
			var ele = this.el;
			FB.XFBML.parse(ele);
			gapi.plusone.go(ele);
			twttr.widgets.load(ele);
 
 			shareTags.addClass("share-buttons-rendered");
 		}
 			
		shareTags.removeClass("hide-element");
		this.$('.sharelink').addClass("hide-element");
	},
	
	template: Mustache.compile($('#news-article-template').html()),
	
	initialize: function() {
    	this.listenTo(this.model, "change", this.updateArticle);
  	},
  	
  	updateArticle: function() {
  		if (this.model.get("unread") == true)
  		{
  			var selfModel = this.model;
  			this.$el.addClass("unread");
  			this.$(".markRead").removeClass("hide-element");
  			this.$(".markUnread").addClass("hide-element");
  		}
  		else
  		{
	  		this.$el.removeClass("unread");
	  		this.$(".markRead").addClass("hide-element");
  			this.$(".markUnread").removeClass("hide-element");
  		}
  		
  		if (this.model.get("saved") == true)
  		{
  			this.$(".markSaved").addClass("hide-element");
  			this.$(".markUnsaved").removeClass("hide-element");
  		}
  		else
  		{
  			this.$(".markSaved").removeClass("hide-element");
  			this.$(".markUnsaved").addClass("hide-element");
  		}
  	},
  	
  	render: function() {
  		var self = this;
  		var feedObj = NewsFeeds.find(function(x) { return x.id == self.model.get("article").feedId; });
  		var newView = null;
  		
  		if (feedObj != null)
  		{
  			newView = $.extend(true, this.model.attributes, {
	  			feed: feedObj.get("feed"),
  				feedObj: feedObj
  			});
  		}
  		else
  		{
  			newView = this.model.attributes;
  		}
  		
  		// Format date in a friendlier manner.
  		newView.article.friendlyPubDate = new Date(newView.article.pubDate).toLocaleString();
  		this.$el.html($.parseHTML(this.template(newView)));
  		this.updateArticle();
  		  		
  		// Make all <a> links in feed open a new window.
  		this.$("a").each(function(i) {
  			if (this.href) {
  				this.target = "_blank";
  			}
  		});
  		
  		return this;
  	}
});

}).call(this);
