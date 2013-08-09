(function(){

NewsFeedView = Backbone.View.extend({
	tagName: 'li',
	
	className: "feed",
	
	template: Mustache.compile($('#news-feed-row-template').html()),
	
	initialize: function() {
    	this.listenTo(this.model, "change", this.render);
  	},
  	
  	render: function() {
  		this.$el.html(this.template(this.model.attributes));
  		return this;
  	},
});

}).call(this);