package moe.dituon.petpet.server;

import moe.dituon.petpet.share.BaseConfigFactory;
import moe.dituon.petpet.share.TextExtraData;

public class POSTParser extends RequestParser {
    public POSTParser(ServerPetService service, String postBody) {
        RequestDTO request = RequestDTO.parse(postBody);
        super.imagePair = service.generateImage(
                request.getKey(),
                BaseConfigFactory.getGifAvatarExtraDataFromUrls(
                        request.getForm().getAvatar(),
                        request.getTo().getAvatar(),
                        request.getGroup().getAvatar(),
                        request.getBot().getAvatar(),
                        request.getRandomAvatarList()
                ), new TextExtraData(
                        request.getForm().getName(),
                        request.getTo().getName(),
                        request.getGroup().getName(),
                        request.getTextList()
                ), null
        );
    }
}
