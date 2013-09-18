if not @NR?
    NR = exports? and exports or @NR = {}
else
    NR = @NR
NR.Views = {}

class NR.Views.NewsFeed extends SimpleMVC.View
    @tag "li"
    @class "newsFeed"
    this.prototype.template = Mustache.compile $("#template-newsFeed").html()
    
    @event "click", ".feedEntry", () ->
        window.app.selectFeed this.model
    
class NR.Views.NewsFeedListing extends SimpleMVC.CollectionView
    @id "feedList"
    @viewType NR.Views.NewsFeed
    this.prototype.template = Mustache.compile $("#template-newsFeedListing").html()
    
    homeSelected: () ->
        this.domObject.children().removeClass("active")
        this.$("#homeLink").addClass("active")
    
    allFeedsSelected: () ->
        this.domObject.children().removeClass("active")
        this.$("#allFeedsLink").addClass("active")
        
    feedSelected: (feed) ->
        this.domObject.children().removeClass("active")
        index = this.model.any((i) -> i.id.toString() == feed.toString())
        if index >= 0
            this._childViews[index].domObject.addClass("active")
            
    @event "click", "#allFeedsLink", () ->
        window.app.selectAllFeeds()

    @event "click", "#homeLink", () ->
        window.app.deselectFeed()

class NR.Views.TopNavBar extends SimpleMVC.View
    @id "top-nav-bar"
    this.prototype.template = Mustache.compile $("#template-topNavBar").html()
    
    @event "click", "#feedLink", (e) ->
        if $("#feedLink").parent().hasClass("disabled")
            e.preventDefault()
            
    _disableFeedLink: () ->
        e = $("#feedLink")
        e.attr "href", "#"
        e.parent().addClass "disabled"
    
    homeSelected: () ->
        this._disableFeedLink()
            
    allFeedsSelected: () ->
        this._disableFeedLink()
        
    feedSelected: (feed) ->
        e = $("#feedLink")
        e.attr "href", feed.feed.link
        e.parent().removeClass "disabled"
          
class NR.Views.WelcomeBlock extends SimpleMVC.View
    @id "welcome-block"
    this.prototype.template = Mustache.compile $("#template-welcomeBlock").html()
    
class NR.Views.NewsArticle extends SimpleMVC.View
    @tag "li"
    @class "newsArticle"
    this.prototype.template = Mustache.compile $("#template-newsArticle").html()
    
class NR.Views.NewsArticleListing extends SimpleMVC.CollectionView
    @id "post-list-ui"
    @listClass "post-list"
    @viewType NR.Views.NewsArticle
    @hideOnStart true
    this.prototype.template = Mustache.compile $("#template-newsArticleListing").html()