if not @NR?
    NR = exports? and exports or @NR = {}
else
    NR = @NR
    
NR.Models = {} 

class NR.Models.MultipleFeedEntry extends SimpleMVC.Model
    @fields "title", "url"
    
class NR.Models.CreateFeedModel extends SimpleMVC.Model
    @fields "baseUrl", "baseHtml", "xpathTitle", "xpathLink", "xpathBody"
    
class NR.Models.NewsFeedInfo extends SimpleMVC.Model
    @fields "feed", "id", "numUnread", "errorsUpdating"
    
    Object.defineProperty(this.prototype, "friendlyNumUnread", {
        get: () -> 
            if this.numUnread.toString() == "0"
                ""
            else
                this.numUnread.toString()
    })
    
class NR.Models.NewsFeedArticleInfo extends SimpleMVC.Model
    @fields "article", "unread", "saved"
    
    Object.defineProperty(this.prototype, "friendlyArticlePubDate", {
        get: () -> new Date(this.article.pubDate).toLocaleString()
    })
    
    # Hack to provide feed information to per-article template.
    Object.defineProperty(this.prototype, "feed", {
        get: () -> 
            if this._props["feed"]?
                this._props["feed"] # for saved posts page
            else
                index = window.app.feedList.any((i) => i.id == this.article.feedId)
                if index >= -1
                    f = window.app.feedList.at index
                    f
        set: (v) -> this._props["feed"] = v
    })
    
class NR.Models.LocalSettings extends SimpleMVC.Model
    @fields "showOnlyUnread", "optedOut", "importQueue", "feedsImported"
    
    Object.defineProperty(this.prototype, "isImporting", {
        get: () -> this.importQueue? && this.importQueue.length > 0
    })
    
    Object.defineProperty(this.prototype, "totalFeeds", {
        get: () -> this.importQueue.length + this.feedsImported
    })
    
class NR.Models.HtmlLocalStorage extends NR.Models.LocalSettings
    _loadFromStorage: () =>
        try
            if localStorage?
                for k,v of localStorage
                    this._props[k] = JSON.parse(v)
        catch error
            # Most likely Safari in private browsing mode. Or old browser.
            
    _saveChanges: () =>
        try
            if localStorage?
                for k,v of this._props
                    localStorage[k] = JSON.stringify(v)
        catch error
            # Most likely Safari in private browsing mode. Or old browser.
    
    constructor: () ->
        super()
        this._loadFromStorage()
        this.registerEvent "change", this._saveChanges