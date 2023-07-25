
Pod::Spec.new do |s|
  s.name         = "RNMergeImages"
  s.version      = "0.2.0"
  s.summary      = "A library to stack multiple images into one"
  s.description  = <<-DESC
                   This pod exposes a package that helps the user combine multiple images into one.
                   DESC
  s.homepage     = "https://github.com/markneh/react-native-merge-images"
  s.license      = "MIT"
  s.author       = { "author" => "contact@mvpstars.com" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/markneh/react-native-merge-images", :tag => "master" }
  s.source_files  = "ios/*.{h,m}"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end