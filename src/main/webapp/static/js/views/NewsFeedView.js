(function(){

NewsFeedView = Backbone.View.extend({
	tagName: 'li',
	
	className: function() {
		if (this.model.id % 2 == 0) {
			return "feed1";
		} else {
			return "feed2";
		}
	},
	
	template: Mustache.compile($('#news-feed-row-template').html()),
	
	initialize: function() {
    	this.listenTo(this.model, "change", this.render);
  	},
  	
  	render: function() {
  		this.$el.html(this.template(this.model.attributes));
  		return this;
  	}
});

}).call(this);