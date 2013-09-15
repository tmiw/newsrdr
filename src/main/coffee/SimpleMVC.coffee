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
        fn.apply(this, args) for fn in this.eventHandlers[name]

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

class SimpleMVC.Controller extends SimpleMVC.Event
    constructor: () -> this.routes = []

    @escapeRegex: (rgx) -> rgx.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&')

    @urlBase: (base = "/") -> this.baseUrl = @escapeRegex(base)

    @route: (path, fn) ->
        # Convert to regexp before inserting into routes list.
        # First, escape regexp characters, then replace :[A-Za-z] with ([^/]+)
        # for :name entries.
        escapedPath = @escapeRegex(path)
        escapedPath = escapedPath.replace(/:[A-Za-z]/, "([^/]+)")
        this.routes[new RegExp(escapedPath)] = fn
	
    @urlBase "/"

    addNewState: (uri) ->
        if history.pushState?
            history.pushState(null, "", uri)
        else
            urlWithoutBase = location.href.replace(/#.*/, "")
            location.replace(urlWithoutBase + "#" + uri)
        this.triggerEvent("navigated", uri)

    navigate: (uri, callRouteFn) =>
        uri = @escapeRegex uri.replace(new RegExp("^" + this.baseUrl), "")
        exists = this.routes.some((r) ->
            v = r.exec uri
            ret = false
            ret = this.routes[r].apply(this, v.slice(1)) if v? && callRouteFn
            ret || (v? && !callRouteFn))
        this.addNewState(uri) if exists
        exists