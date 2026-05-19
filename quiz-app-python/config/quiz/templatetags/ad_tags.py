from django import template

register = template.Library()


class AdBlockNode(template.Node):
    def __init__(self, nodelist):
        self.nodelist = nodelist

    def render(self, context):
        if context.get('user_is_premium'):
            return ''
        return self.nodelist.render(context)


@register.tag('adblock')
def adblock_tag(parser, token):
    nodelist = parser.parse(('endadblock',))
    parser.delete_first_token()
    return AdBlockNode(nodelist)
