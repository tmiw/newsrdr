(function(){

NewsArticleView = Backbone.View.extend({
	tagName: 'li',
	
	className: 'feedpost',
	
	events: {
		"click .title": function() { this.model.set("unread", false); },
		"click .markRead": function() { this.model.set("unread", false); },
		"click .markUnread": function() { this.model.set("unread", true); },
	},
	
	template: Mustache.compile($('#news-article-template').html()),
	
	initialize: function() {
    	this.listenTo(this.model, "change", this.render);
  	},
  	
  	render: function() {
  		var self = this;
  		var feedObj = NewsFeeds.find(function(x) { return x.id == self.model.get("article").feedId; });
  		var newView = $.extend(true, this.model.attributes, {
  			feed: feedObj.get("feed"),
  			feedObj: feedObj
  		});
  		this.$el.html(this.template(newView));
  		if (this.model.get("unread") == true)
  		{
  			var selfModel = this.model;
  			this.$el.addClass("unread");
  			this.$(".markRead").removeClass("hide-element");
  			this.$(".markUnread").addClass("hide-element");
  			/*this.$el.waypoint(function() {
  				selfModel.set("unread", false);
  			}, {
  				offset: function() {
    				return -$(this).height();
  				}
  			});*/
  		}
  		else
  		{
	  		this.$el.removeClass("unread");
	  		this.$(".markRead").addClass("hide-element");
  			this.$(".markUnread").removeClass("hide-element");
  		}
  		return this;
  	}
});

}).call(this);