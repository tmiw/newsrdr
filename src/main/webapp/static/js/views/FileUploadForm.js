$(function() {

FileUploadForm = Backbone.View.extend({

	el: $("#uploadForm"),
	
	template: Mustache.compile($('#import-feeds-template').html()),

	initialize: function() { },

	events: {
		"click .closeButton": function() { this.hide(); }
	},
		
	show: function() { this.$el.html(this.template({})); this.$el.removeClass("hide-element"); },
	
	hide: function() { $("#uploadForm").empty(); this.$el.addClass("hide-element"); },
	
	submit: function() {
		$("#uploading").removeClass("hide-element");
		return true;
	},
	
	done: function(result) {
		$("#uploading").addClass("hide-element");
		
		if (!result.success)
		{
		    var errorText = "Error encountered while uploading file.";
		    if (result.reason == "forgot_file")
		    {
		        errorText = "Please select a file and try again.";
		    }
		    else if (result.reason == "cant_parse")
		    {
		        errorText = "The file provided is not a valid OPML file. Select another file and try again.";
		    }
		    else if (result.reason == "not_authorized")
		    {
		        // Force user to login screen.
		        location.reload();
		    }
		    else if (result.reason == "too_big")
		    {
		        errorText = "The file provided is too big to be parsed. Select another file and try again.";
		    }
		    
		    noty({ text: errorText, layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" });
		} 
		else
		{
			// Queue up feeds for processing.
			this.hide();
			AppController.addBulkFeeds(result.feeds);
		}
	}

});

});