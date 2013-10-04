if not @NR?
    NR = exports? and exports or @NR = {}
else
    NR = @NR
NR.Views = {}

class NR.Views.NewsFeed extends SimpleMVC.View
    @tag "li"
    @class "newsFeed"
    this.prototype.template = Mustache.compile $("#template-newsFeed").html()
    
    @event "click", ".feedEntry", (e) ->
        window.app.selectFeed this.model
        e.preventDefault()
    
class NR.Views.NewsFeedListing extends SimpleMVC.CollectionView
    @id "feedList"
    @viewType NR.Views.NewsFeed
    this.prototype.template = Mustache.compile $("#template-newsFeedListing").html()
    this.prototype.titleTemplate = Mustache.compile $("#template-title").html()
    
    sortFn: (first, second) ->
        fModel = first.model
        sModel = second.model
        
        diff = -(fModel.numUnread - sModel.numUnread)
        if diff == 0
            diff = fModel.feed.title.localeCompare sModel.feed.title
        
        if diff < 0
            -1
        else if diff > 0
            1
        else
            0
    
    eqFn: (first, second) ->
        first.id == second.id
        
    constructor: (coll) ->
        super(coll)
        this._totalUnread = 0
        
    _updateAllUnread: () =>
        text = this._totalUnread.toString()
        if text == "0"
            text = ""
        $("#totalUnread").text(text)
        document.title = this.titleTemplate.call(this, {numUnread: text, allSelected: this._isAllFeedsSelected, feedSelected: this._isFeedSelected, feedTitle: this._feedTitle})
        
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
        this._isAllFeedsSelected = false
        this._isFeedSelected = false
        this._updateAllUnread()
        
    allFeedsSelected: () ->
        this.domObject.children().removeClass("active")
        this.$("#allFeedsLink").addClass("active")
        this._isAllFeedsSelected = true
        this._isFeedSelected = false
        this._updateAllUnread()
        
    feedSelected: (feed) ->
        this._isAllFeedsSelected = false
        this._isFeedSelected = true
        
        this.domObject.children().removeClass("active")
        for k,v of this._childViews
            if this._childViews[k].model.id.toString() == feed.toString()
                this._childViews[k].domObject.addClass("active")
                this._feedTitle = this._childViews[k].model.feed.title
        this._updateAllUnread()

    @event "click", "#allFeedsLink", (e) ->
        window.app.selectAllFeeds()
        e.preventDefault()

    @event "click", "#homeLink", (e) ->
        window.app.deselectFeed()
        e.preventDefault()

class NR.Views.CreateFeedWindow extends SimpleMVC.View
    @id "createFeed"
    @hideOnStart true
    
    _canSave: () =>
        this.model.xpathTitle? and this.model.xpathLink?
            
    _updateGlyphs: () =>
        if this.model.xpathTitle?
            $("#feed-title-set .glyphicon").show()
        else
            $("#feed-title-set .glyphicon").hide()
            
        if this.model.xpathLink?
            $("#feed-link-set .glyphicon").show()
        else
            $("#feed-link-set .glyphicon").hide()
        
        if this.model.xpathBody?
            $("#feed-description-set .glyphicon").show()
        else
            $("#feed-description-set .glyphicon").hide()
        
        # disable save button if needed
        if this._canSave()
            $('#saveCreatedFeedButton').removeAttr("disabled")
        else
            $('#saveCreatedFeedButton').attr("disabled", "disabled")
            
    _onChangeXpathTitle: () =>
        this._suppressRender = true
        this._updateGlyphs()
            
    _onChangeXpathLink: () =>
        this._suppressRender = true
        this._updateGlyphs()
            
    _onChangeXpathBody: () =>
        this._suppressRender = true
        this._updateGlyphs()
    
    _disableButtons: () =>
        this.domObject.find("#feed-title-set").removeClass("btn-primary")
        this.domObject.find("#feed-link-set").removeClass("btn-primary")
        this.domObject.find("#feed-description-set").removeClass("btn-primary")
    
    _isLinkEnabled: () =>
        this.domObject.find("#feed-link-set").hasClass("btn-primary")
        
    _isTitleEnabled: () =>
        this.domObject.find("#feed-title-set").hasClass("btn-primary")
        
    _getXPath: (ele) ->
        if ele.id? and ele.id.length > 0
            "//*[@id='" + ele.id + "']"
        else if ele == $("#createFeedDocument")[0].contentWindow.document.body
            "/" + ele.tagName.toLowerCase()
        else
            this._getXPath(ele.parentNode) + "/" + ele.tagName.toLowerCase()
            
    _handleClickedItem: (e) =>
        e.preventDefault()
        target = $(e.target)
        if this._isLinkEnabled()
            if target.prop("tagName") != "A"
                parent = target.parents().filter(() -> $(this).prop("tagName") == "A").first()
            else
                parent = target
            xpathSuffix = "/@href"
        else
            if target.css("display") == "inline"
                parent = target.parents().filter(() -> $(this).css("display") != "inline").first()
            else
                parent = target
        
            if this._isTitleEnabled()
                xpathSuffix = "/descendant::text()"
            else
                xpathSuffix = "/child::node()" # Will return a node set on the server.
        
        xpath = (this._getXPath parent[0]) + xpathSuffix
        
        if this._isLinkEnabled()
            this.model.xpathLink = xpath
            this._setCssClass this._linkDomObject, parent, "nr-link-selected"
            this._linkDomObject = parent
        else if this._isTitleEnabled()
            this.model.xpathTitle = xpath
            this._setCssClass this._titleDomObject, parent, "nr-title-selected"
            this._titleDomObject = parent
        else
            this.model.xpathBody = xpath
            this._setCssClass this._bodyDomObject, parent, "nr-body-selected"
            this._bodyDomObject = parent
    
    _clearCssClass: (oldObject, aClass) =>
        if oldObject?
            oldObject.removeClass(aClass)
            
    _setCssClass: (oldObject, newObject, aClass) =>
        this._clearCssClass oldObject, aClass
        newObject.addClass(aClass)
        
    @event "click", "#feed-title-set", () ->
        this._disableButtons()
        this.domObject.find("#feed-title-set").addClass("btn-primary")
    
    @event "click", "#feed-link-set", () ->
        this._disableButtons()
        this.domObject.find("#feed-link-set").addClass("btn-primary")
        
    @event "click", "#feed-description-set", () ->
        this._disableButtons()
        this.domObject.find("#feed-description-set").addClass("btn-primary")
    
    @event "click", "#reset-create-feed-btn", () ->
        this._disableButtons()
        this._clearCssClass this._linkDomObject, "nr-link-selected"
        this._clearCssClass this._titleDomObject, "nr-title-selected"
        this._clearCssClass this._bodyDomObject, "nr-body-selected"
        this._linkDomObject = null
        this._titleDomObject = null
        this._bodyDomObject = null
        this.model.xpathTitle = null
        this.model.xpathLink = null
        this.model.xpathBody = null
    
    @event "click", "#saveCreatedFeedButton", () ->
        urlField = "url=" + encodeURIComponent(this.model.baseUrl)
        xpathTitleField = "titleXPath=" + encodeURIComponent(this.model.xpathTitle)
        xpathLinkField = "linkXPath=" + encodeURIComponent(this.model.xpathLink)
        qs = "?" + urlField + "&" + xpathTitleField + "&" + xpathLinkField
        if this.model.xpathBody?
            qs = qs + "&bodyXPath=" + encodeURIComponent(this.model.xpathBody)
        window.app.addFeed (location.protocol + "//" + location.host + "/feeds/generate.rss" + qs)
        this.hide()
        
    render: () =>
        if not this.model? || (this.model? && not this._suppressRender)
            iframe = $("#createFeedDocument")[0]
            iframe.contentWindow.document.removeEventListener 'click', this._handleClickedItem
            iframe.contentWindow.location.href = "about:blank"
        
            if this.model?
                iframeDoc = iframe.contentWindow.document
                iframeDoc.open()
                iframeDoc.write("<link href=\"//" + location.host + "/static/css/newsrdr.css\" rel=\"stylesheet\" type=\"text/css\" />")
                iframeDoc.write("<base href=\"" + $("#addFeedUrl").val() + "\"/>")
                iframeDoc.write(this.model.baseHtml)
                iframeDoc.close()
                
                iframeDoc.addEventListener 'click', this._handleClickedItem
                
                this.model.unregisterEvent("change:xpathTitle", this._onChangeXpathTitle)
                this.model.unregisterEvent("change:xpathLink", this._onChangeXpathLink)
                this.model.unregisterEvent("change:xpathBody", this._onChangeXpathBody)
                
                this.model.registerEvent("change:xpathTitle", this._onChangeXpathTitle)
                this.model.registerEvent("change:xpathLink", this._onChangeXpathLink)
                this.model.registerEvent("change:xpathBody", this._onChangeXpathBody)
                
                this._updateGlyphs()
        else
            this._suppressRender = false
            
    show: () =>
        this.domObject.modal()
    
    hide: () =>
        this.domObject.modal('hide')
        
class NR.Views.TopNavBar extends SimpleMVC.View
    @id "top-nav-bar"
    this.prototype.template = Mustache.compile $("#template-topNavBar").html()
    
    @event "click", "#removeFeedLink", (e) ->
        e.preventDefault()
        if not $("#removeFeedLink").parent().hasClass("disabled")
            $("#removeFeedConfirm").modal()
            
    @event "click", "#feedLink", (e) ->
        if $("#feedLink").parent().hasClass("disabled")
            e.preventDefault()
    
    @event "click", "#markAllReadLink", (e) ->
        e.preventDefault()
        if not $("#markAllReadLink").parent().hasClass("disabled")
            window.app.markAllRead()
            
    @event "click", "#showOnlyUnreadLink", (e) ->
        e.preventDefault()
        if not $("#showOnlyUnreadLink").parent().hasClass("disabled")
            window.app.toggleShowUnread()
    
    @event "click", "#optOutSharingLink", (e) ->
        window.app.toggleOptOut()
        e.preventDefault()
        
    @event "click", "#addFeedLink", (e) ->
        e.preventDefault()
        window.app.showAddFeedWindow()
    
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
        this._disableLink("#markAllReadLink")
            
    allFeedsSelected: () ->
        this._disableLink("#feedLink")
        this._disableLink("#removeFeedLink")
        this._enableLink("#markAllReadLink")
        
    feedSelected: (feed) ->
        this._enableLink("#feedLink").attr "href", feed.feed.link
        this._enableLink("#removeFeedLink")
        this._enableLink("#markAllReadLink")
    
    _updateFeedsImported: (newVal, oldVal) =>
        $("#feedsImportedCount").text(this.model.feedsImported.toString())
        $("#totalFeedsCount").text(this.model.totalFeeds.toString())
        this._suppressRender = true
        
    render: () =>
        if not this.model? || (this.model? && not this._suppressRender)
            super()
            if this.model?
                this.model.unregisterEvent("change:feedsImported", this._updateFeedsImported)
                this.model.registerEvent("change:feedsImported", this._updateFeedsImported)
            
            if this._ajaxReferenceCount == 0
                this.$(".spinner").hide()
                this.$(".spinner-inv").hide()
            else
                if this.domObject.hasClass("affix")
                    this.$(".spinner-inv").show()
                else
                    this.$(".spinner").show()
        else
            this._suppressRender = false

    _beforeAjax: () =>
        this._ajaxReferenceCount = this._ajaxReferenceCount + 1
        if this._ajaxReferenceCount == 1
            if this.domObject.hasClass("affix")
                this.$(".spinner-inv").show()
            else
                this.$(".spinner").show()
    
    _afterAjax: () =>
        this._ajaxReferenceCount = this._ajaxReferenceCount - 1
        if this._ajaxReferenceCount == 0
            this.$(".spinner").hide()
            this.$(".spinner-inv").hide()
            
    constructor: () ->
        super()
        
        # Spinner control
        this._ajaxReferenceCount = 0
        document.addEventListener "NR.API.beforeXHR", this._beforeAjax
        document.addEventListener "NR.API.afterXHR", this._afterAjax
        
class NR.Views.WelcomeBlock extends SimpleMVC.View
    @id "welcome-block"
    this.prototype.template = Mustache.compile $("#template-welcomeBlock").html()
    
class NR.Views.NewsArticle extends SimpleMVC.View
    @tag "div"
    @class "newsArticle"
    this.prototype.template = Mustache.compile $("#template-newsArticle").html()
    this.prototype.shareTemplate = Mustache.compile $("#template-shareArticle").html()
    
    @event "click", ".markReadButton", (e) ->
        window.app.togglePostAsRead this.model
    
    @event "click", ".saveButton", (e) ->
        window.app.toggleSavePost this.model
    
    @event "click", ".panel-title", (e) ->
        if this.model.unread
            window.app.togglePostAsRead this.model
            
    _updateUnread: (newVal, oldVal) =>
        btn = this.domObject.find ".markReadButton"
        panel = this.domObject.find ".article-panel"
        if not newVal
            btn.addClass "btn-primary"
            btn.removeClass "btn-default"
            panel.removeClass "unread"
        else
            btn.removeClass "btn-primary"
            btn.addClass "btn-default"
            panel.addClass "unread"
        this._suppressRender = true
    
    _updateSaved: (newVal, oldVal) =>
        btn = this.domObject.find ".saveButton"
        if newVal
            btn.addClass "btn-primary"
            btn.removeClass "btn-default"
        else
            btn.removeClass "btn-primary"
            btn.addClass "btn-default"
        this._suppressRender = true
    
    _leakCleanup: () ->
        # We need to navigate all iframes inside the article to about:blank first.
        # Failure to do so results in memory leaks.
        frameList = this.$('iframe')
        for i in frameList
            i.contentWindow.location.href = "about:blank"
        frameList.remove()
        
        # Hide any visible popovers.
        shareBtn = this.domObject.find(".shareButton")
        shareBtn.popover("destroy")
        
    render: () =>
        if not this.model? || (this.model? && not this._suppressRender)
            this._leakCleanup()
            super()
            if this.model?
                this.model.unregisterEvent("change:unread", this._updateUnread)
                this.model.registerEvent("change:unread", this._updateUnread)
                this.model.unregisterEvent("change:saved", this._updateSaved)
                this.model.registerEvent("change:saved", this._updateSaved)
                
                shareBtn = this.domObject.find(".shareButton")
                shareBtn.popover({
                    html: true
                    placement: 'top'
                    content: this.shareTemplate.call this, this.model
                })
                shareBtn.on("shown.bs.popover", () =>
                    ele = $("#share-items-" + this.model.article.id)
                    FB.XFBML.parse(ele[0])
                    gapi.plusone.go(ele[0])
                    twttr.widgets.load(ele[0])
                )
                
                # Make all <a> links in feed open a new window.
                this.$("a").each((i) ->
                    if (this.href)
                        this.target = "_blank"
                )
        
                if this.model.saved
                    btn = this.domObject.find ".saveButton"
                    btn.addClass "btn-primary"
                    btn.removeClass "btn-default"
                if not this.model.unread
                    btn = this.domObject.find ".markReadButton"
                    btn.addClass "btn-primary"
                    btn.removeClass "btn-default"
                    panel = this.domObject.find ".article-panel"
                    panel.removeClass "unread"
        else
            this._suppressRender = false
            
    destroy: () =>
        # We need to navigate all iframes inside the article to about:blank first.
        # Failure to do so results in memory leaks.
        this._leakCleanup()
        super()
        
class NR.Views.NewsArticleListing extends SimpleMVC.CollectionView
    @id "post-list-ui"
    @listClass "post-list"
    @viewType NR.Views.NewsArticle
    @hideOnStart true
    this.prototype.template = Mustache.compile $("#template-newsArticleListing").html()
    
    _isScrolledTo: (item) =>
        docViewTop = $(window).scrollTop()
        docViewBottom = docViewTop + $(window).height()
        elemTop = item.offset().top
        elemTop < docViewBottom
        
    _onScroll: () =>
        bottomView = this._childViews[this._childViews.length - 1]
        if bottomView? && this._isScrolledTo bottomView.domObject
            window.app.fetchMorePosts()
            
    constructor: (model) ->
        super model
        
        # Set up infinite scroll handler
        $(document).scroll(this._onScroll)