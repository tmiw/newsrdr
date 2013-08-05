(function(){

NewsArticleView = Backbone.View.extend({
	tagName: 'li',
	
	className: 'feedpost',
	
	events: {
		"click .title": function() { this.model.set("unread", false); }
	},
	
	template: Mustache.compile($('#news-article-template').html()),
	
	initialize: function() {
    	this.listenTo(this.model, "change", this.render);
  	},
  	
  	render: function() {
  		var self = this;
  		var newView = $.extend(true, this.model.attributes, {
  			feed: NewsFeeds.find(function(x) { return x.id == self.model.get("article").feedId; }).get("feed")
  		});
  		this.$el.html(this.template(newView));
  		if (this.model.get("unread") == true)
  		{
  			var selfModel = this.model;
  			this.$el.addClass("unread");
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
  		}
  		return this;
  	}
});

}).call(this);