if not @NR?
    NR = exports? and exports or @NR = {}
else
    NR = @NR
    
NR.Models = {} 

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
                    this._props[k] = v
        catch error
            # Most likely Safari in private browsing mode. Or old browser.
            
    _saveChanges: () =>
        try
            if localStorage?
                for k,v of this._props
                    localStorage[k] = v
        catch error
            # Most likely Safari in private browsing mode. Or old browser.
    
    constructor: () ->
        super()
        this._loadFromStorage()
        this.registerEvent "change", this._saveChanges