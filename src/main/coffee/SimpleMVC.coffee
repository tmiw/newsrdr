SimpleMVC = exports? and exports or @SimpleMVC = {}

class SimpleMVC.Event
    ensureInitialized: (name) ->
        this.eventHandlers = [] if !this.eventHandlers?
        this.eventHandlers[name] = [] if !this.eventHandlers[name]?

    registerEvent: (name, fn) -> 
        this.ensureInitialized(name)
        this.eventHandlers[name].push(fn)

    unregisterEvent: (name, fn) ->
        this.ensureInitialized(name)
        index = this.eventHandlers[name].indexOf(fn)
        this.eventHandlers[name].splice(index, 1) if index >= 0

    unregisterAllEvents: (name) ->
        this.eventHandlers = [] if !name?
        this.eventHandlers[name] = []

    triggerEvent: (name, args...) ->
        this.ensureInitialized(name)
        fn.apply(this, args) for idx, fn of this.eventHandlers[name]

class SimpleMVC.Model extends SimpleMVC.Event
    constructor: () -> 
        this._props = {}

    @defineGetterSetter: (name) ->
        Object.defineProperty(this.prototype, name, {
            get: () -> this._props[name],
            set: (val) -> 
                this._props[name] = val
                this.triggerEvent("change:" + name, val)
                this.triggerEvent("change", this)
        })

    @fields: (names...) ->
        @defineGetterSetter(name) for name in names

class SimpleMVC.Collection extends SimpleMVC.Event
    constructor: () ->
        this._coll = []
    
    reset: (x) ->
        this._coll = []
        this.triggerEvent "reset", this
        this.add(i) for i in x
        
    add: (x) ->
        this._coll.push x
        this.triggerEvent "add", this, this._coll.length - 1
    
    insert: (x, i) ->
        this._coll.splice i, 0, x
        this.triggerEvent "add", this, i
        
    removeAt: (i) ->
        removed = this._coll.splice i, 1
        this.triggerEvent "remove", this, removed[0]
    
    remove: (x) ->
        i = this._coll.indexOf x
        if i > -1
            this._coll.splice i, 1
            this.triggerEvent "remove", this, x
    
    each: (fn) ->
        fn.call(this, i) for i in this._coll
            
class SimpleMVC.View extends SimpleMVC.Event
    # Default properties
    hideOnStart: false
    events: {}
    
    @tag: (name) -> this.prototype.outerTag = name
    @class: (name) -> this.prototype.outerClass = name
    @id: (id) -> this.prototype.outerId = id
    
    @hideOnStart: (v) -> this.prototype.hideOnStart = v
    
    @event: (eventName, id, fn) ->
        this.prototype.events[eventName] = {} if not this.prototype.events[eventName]?
        this.prototype.events[eventName][id] = fn
    
    delegateEvents: () ->
        for k,v of this.events
            for k2,v2 of v
                this.domObject.on(k, k2, v2)
    
    undelegateEvents: () ->
        for k,v of this.events
            for k2,v2 of v
                this.domObject.off(k, k2, v2)
                
    constructor: () ->
        if this.outerId?
            this.domObject = $("#" + this.outerId)
        else
            this.domObject = $(this.outerTag + " ." + this.outerClass)
        
        this.delegateEvents()
        this.hide() if this.hideOnStart
        
    show: () ->
        this.domObject.show()
    
    hide: () ->
        this.domObject.hide()
    
    render: () =>
        # Default render operation. If provided, @template should be a JS/CoffeeScript
        # function that takes the model object as its sole parameter and 
        # returns HTML.
        if @template?
            this.domObject.html(@template(this.model))
        else
            this.domObject.html(this.model?.toString())
    
    # Use JS getters/setters for this.model. This will allow us to clean up 
    # event handlers as needed.
    Object.defineProperty(this.prototype, "model", {
        get: () -> this._model,
        set: (val) -> 
            if this._model?
                this._model.unregisterEvent("change", this.render)
            this._model = val
            if this._model?
                this._model.registerEvent("change", this.render)
                this.render()
    })

class SimpleMVC.Controller extends SimpleMVC.Event
    this.prototype.baseUrl = "/"
     
    @escapeRegex: (rgx) -> rgx.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&')

    @urlBase: (base = "/") -> this.prototype.baseUrl = @escapeRegex(base)

    @route: (path, fn) ->
        # Convert to regexp before inserting into routes list.
        # First, escape regexp characters, then replace :[A-Za-z] with ([^/]+)
        # for :name entries.
        escapedPath = @escapeRegex(path)
        escapedPath = escapedPath.replace(/:[A-Za-z]+/, "([^/]+)")
        this.prototype.routes = {} if not this.prototype.routes?
        this.prototype.routes[escapedPath] = fn

    addNewState: (uri) ->
        if history.pushState?
            history.pushState(null, "", uri)
        else
            urlWithoutBase = location.href.replace(/#.*/, "")
            location.replace(urlWithoutBase + "#" + uri)
        this.triggerEvent("navigated", uri)

    navigate: (uri, callRouteFn = false) ->
        shortUri = uri.replace(new RegExp("^" + this.baseUrl), "")
        ret = false
        for k,v of this.routes
            matches = new RegExp(k).exec shortUri
            if not ret
                ret = v.apply(this, matches.slice(1)) if matches? && callRouteFn
                ret = matches? && !callRouteFn if !callRouteFn
        this.addNewState(uri) if ret
        ret
        
    start: (callRouteFn = true) =>
        this.navigate(location.pathname, callRouteFn)