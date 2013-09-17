if not @NR?
    NR = exports? and exports or @NR = {}
else
    NR = @NR
    
NR.Models = {} 

class NR.Models.NewsFeedInfo extends SimpleMVC.Model
    @fields "feed", "id", "numUnread", "errorsUpdating"
    
class NR.Models.NewsFeedArticleInfo extends SimpleMVC.Model
    @fields "article", "unread", "saved"