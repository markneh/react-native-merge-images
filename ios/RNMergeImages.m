
#import "RNMergeImages.h"

@implementation RNMergeImages

- (NSString *)saveImage:(UIImage *)image {
    NSString *fileName = [[NSProcessInfo processInfo] globallyUniqueString];
    NSString *fullPath = [NSString stringWithFormat:@"%@%@.jpg", NSTemporaryDirectory(), fileName];
    NSData *imageData = UIImageJPEGRepresentation(image, 0.75);
    [imageData writeToFile:fullPath atomically:YES];
    return fullPath;
}

- (NSURL *)applicationDocumentsDirectory
{
    return [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
}

RCT_EXPORT_MODULE()

+ (BOOL)requiresMainQueueSetup {
    return YES;
}

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

- (NSDictionary *)constantsToExport
{
    return @{
             @"Size": @{
                    @"largest": @"1",
                    @"smallest": @"0",
                },
             @"Target": @{
                 @"temp": @"1",
                 @"disk": @"0",
                 }
             };
}

RCT_EXPORT_METHOD(merge:(NSArray *)imagePaths
                  options:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSMutableArray *images = [@[] mutableCopy];
        CGSize contextSize = CGSizeMake(0, 0);
        NSNumber *maxWidth = options[@"maxWidth"];

        if (imagePaths.count == 0) {
            reject(@"empty", @"No images were provided", [NSError errorWithDomain:@"RNMergeImages" code:-1 userInfo:@{
                NSLocalizedDescriptionKey: @"Provided array of image paths is empty"
            }]);
            return;
        }

        for (id tempObject in imagePaths) {

            NSUInteger index = [imagePaths indexOfObject:tempObject];

            if (![tempObject isKindOfClass:[NSString class]]) {
                NSString *errorMessage = [NSString stringWithFormat:@"Provided array of image paths should be array of path strings. The item at index %lu is not a string", (unsigned long)index];
                reject(@"invalid format", errorMessage, [NSError errorWithDomain:@"RNMergeImages" code:-2 userInfo:@{
                    NSLocalizedDescriptionKey: errorMessage
                }]);
                return;
            }

            NSURL *URL = [RCTConvert NSURL:tempObject];
            NSData *imgData = [[NSData alloc] initWithContentsOfURL:URL];
            if (imgData != nil)
            {
                UIImage *image = [[UIImage alloc] initWithData:imgData];
                [images addObject:image];

                CGSize clampedImageSize = [RNMergeImages clampedImageSize:image.size maxWidth:maxWidth];

                CGFloat width = MAX(clampedImageSize.width, contextSize.width);
                CGFloat height = contextSize.height + clampedImageSize.height;
                contextSize = CGSizeMake(width, height);
            }
        }

        if (images.count == 0) {
            reject(@"final size calc", @"Failed to calculate final image size", [NSError errorWithDomain:@"RNMergeImages" code:-1 userInfo:@{
                NSLocalizedDescriptionKey: @"Failed to calculate final image size"
            }]);
            return;
        }

        // create context with size
        UIGraphicsBeginImageContext(contextSize);
        // loop through image array

        CGFloat y = 0;
        for (UIImage *image in images) {
            CGSize clampedImageSize = [RNMergeImages clampedImageSize:image.size maxWidth:maxWidth];
            [image drawInRect:CGRectMake(0, y, clampedImageSize.width, clampedImageSize.height)];
            y += clampedImageSize.height;
        }
        // creating final image
        UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
        // save final image in temp
        NSString *imagePath = [self saveImage:newImage];
        //resolve with image path
        resolve(@{@"path":imagePath, @"width":[NSNumber numberWithFloat:newImage.size.width], @"height":[NSNumber numberWithFloat:newImage.size.height]});
    } @catch (NSException *exception) {
        NSMutableDictionary *info = [NSMutableDictionary dictionary];
        [info setValue:exception.name forKey:@"ExceptionName"];
        [info setValue:exception.reason forKey:@"ExceptionReason"];
        [info setValue:exception.callStackReturnAddresses forKey:@"ExceptionCallStackReturnAddresses"];
        [info setValue:exception.callStackSymbols forKey:@"ExceptionCallStackSymbols"];
        [info setValue:exception.userInfo forKey:@"ExceptionUserInfo"];

        NSError *error = [[NSError alloc] initWithDomain:@"RNMergeImages" code:-2 userInfo:info];
        reject(@"exception", @"Something went wrong while trying to merge images", error);
    }
}

+ (CGSize)clampedImageSize:(CGSize)size maxWidth:(NSNumber *)maxWidth {

    if (!maxWidth) {
        return size;
    }

    CGFloat imageRatio = size.width / size.height;
    CGFloat imageWidth = maxWidth ? MIN((CGFloat)[maxWidth floatValue], size.width) : size.width;
    CGFloat imageHeight = maxWidth ? floor(imageWidth / imageRatio) : size.height;

    return (CGSize) { .width = imageWidth, .height = imageHeight };
}

@end
