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
    
    constructor: (coll) ->
        super(coll)
        this._totalUnread = 0
        
    _updateAllUnread: () =>
        text = this._totalUnread.toString()
        if text == "0"
            text = ""
        $("#totalUnread").text(text)
    
    _onUnreadChange: (newVal, oldVal) =>
        this._totalUnread = this._totalUnread - oldVal + newVal
        this._updateAllUnread()
        
    _onAdd: (coll, index) =>
        super coll, index
        item = coll.at(index)
        item.registerEvent "change:numUnread", this._onUnreadChange
        this._totalUnread = this._totalUnread + item.numUnread
        this._updateAllUnread()
        
    _onRemove: (coll, index) =>
        index.unregisterEvent "change:numUnread", this._onUnreadChange
        this._totalUnread = this._totalUnread - index.numUnread
        super coll, index
        this._updateAllUnread()
        
    _onReset: (coll) =>
        super coll
        this._totalUnread = 0
        this._updateAllUnread()
        
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
    
    @event "click", "#removeFeedLink", (e) ->
        if $("#removeFeedLink").parent().hasClass("disabled")
            e.preventDefault()
        else
            $("#removeFeedConfirm").modal()
            
    @event "click", "#feedLink", (e) ->
        if $("#feedLink").parent().hasClass("disabled")
            e.preventDefault()
            
    _disableLink: (id) ->
        e = $(id)
        e.attr "href", "#"
        e.parent().addClass "disabled"
    
    _enableLink: (id) ->
        e = $(id)
        e.parent().removeClass "disabled"
        e
        
    homeSelected: () ->
        this._disableLink("#feedLink")
        this._disableLink("#removeFeedLink")
            
    allFeedsSelected: () ->
        this._disableLink("#feedLink")
        this._disableLink("#removeFeedLink")
        
    feedSelected: (feed) ->
        this._enableLink("#feedLink").attr "href", feed.feed.link
        this._enableLink("#removeFeedLink")
          
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