$(document).ready(
    function () {
        var query = $('#query')
        var rc = $('#rc')
        var results = $('#results')
        var dis = 'disabled'
        var bc = 'background-color'
        var color = query.css(bc)

        var search = function (event) {
            $.get('/content', { q: query.val(), rc: rc.is(':checked') ? 1 : '' },
                function (data) {
                    results.html(
                        data.length < 1 ? '<br/>[no results]'
                          : typeof data[0].id == 'undefined' ?
                                '<pre>' + data.join('<br/>') + '</pre>'
                              : data.map(function (e) { return '<br/><b>' + e.id + '</b><br/>' + e.text }).join('<br/>')
                        )
                    query.focus()
                })
        }
        var del = function (event) {
            $.ajax({
                type: 'DELETE',
                // http://bugs.jquery.com/ticket/11586
                url: '/content?q=' + encodeURIComponent(query.val()),
              //url: '/content',
              //data: { q: query.val() },
                success: function () { results.html('<br/>[deleted]') }
            })
        }
        var reset = function (event) {
            $.post('/reset', '',
                function () {
                    query.val('')
                    results.html('<br/>[Redis reset]')
                })
        }

        query.ajaxStart(
            function () {
                results.text = ''
                query.attr(dis, dis)
                query.css(bc, 'lightgrey')
            })
        query.ajaxStop(
            function () {
                query.removeAttr(dis)
                query.css(bc, color)
                query.focus()
            })
        results.ajaxError(
            function (event, req, settings) {
                $(this).html(req.status + ' ' + req.statusText + ' ' + req.responseText)
            })
        $("#search").click(search)
        query.keypress(
            function (event) {
                if (event.which == 13 )
                    search()
            })
        $("#delete").click(del)
        $("#reset").click(reset)

        query.focus()
    })
