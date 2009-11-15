
class VehicleImage
  import java.awt.image.BufferedImage
  attr_accessor :images_3d, :images_2d
  def initialize(path)
    puts "Loading vehicle: #{path}"
    if path =~ /\.srf$/i
      @full_image = load_srf(path)
    else
      @full_image = javax.imageio.ImageIO.read(java.io.File.new(path))
    end

    # 3d images are biggest, so assume the width of the image tells us the size of those.
    @size_3d = (@full_image.width / 360.0).floor * 10

    # Min size for 2d is 60px, so if it's big enough to hold four sets of images,
    # taking into account the 3d size, then assume it's an animated one. 
    if @full_image.height >= (@size_3d * 2) + (60 * 2)
      @animated = true
      # Animated sizes match the regular/rotating versions, so figure out 2d size based on that.
      @size_2d = ((@full_image.height - @size_3d * 2) / 20.0).floor * 10
    else
      @animated = false
      # Assume the 2d images take up the rest of the height.
      @size_2d = ((@full_image.height - @size_3d) / 10.0).floor * 10
    end
    
    puts "  Animated: #{@animated ? 'Yes' : 'No'}"
    puts "  3D size: #{@size_3d}"
    puts "  2D size: #{@size_2d}"

    @images_3d = (0..35).to_a.collect { |x| @full_image.get_subimage(x*@size_3d, 0, @size_3d, @size_3d) }
    @images_2d = (0..35).to_a.collect { |x| @full_image.get_subimage(x*@size_2d, @size_3d, @size_2d, @size_2d) }
    
  end
  def load_srf(path)
    srf_data = File.read(path)
    raise "Invalid SRF File" unless srf_data[0,16] == 'GARMIN BITMAP 01'
    
    subimage_count = srf_data[24,4].unpack('V').first
    puts "  Subsections: #{subimage_count}"

    # Go through the various header strings to find where the first subimage is.
    subimage_base = 32
    subimage_base += srf_data[subimage_base,4].unpack('V').first + 8 # The "578" thing.
    subimage_base += srf_data[subimage_base,4].unpack('V').first + 8 # The revision number.
    subimage_base += srf_data[subimage_base,4].unpack('V').first + 4 # The product code.
    
    total_height = 0
    max_width = 0
    subimages = []
    subimage_count.times do |i|
      height,width = srf_data[subimage_base + 12, 4].unpack('vv')
      puts "  Section #{i+1}: #{width}x#{height}"
      total_height += height
      max_width = [max_width, width].max
      
      alpha_data = srf_data[subimage_base + 32, width*height].unpack('c*')
      rgb_data = srf_data[subimage_base + 40 + width*height, width*height*2].unpack('v*')
      
      pos = 0
      scanline_data = []
      height.times do |y|
        width.times do |x|
          a = (alpha_data[pos] & 255) << 1
          a = (a >= 254) ? 0 : 255 - a
          c = rgb_data[pos]
          r,g,b = (c & 0xf800) << 8, (c & 0x07c0) << 5, (c & 0x001f) << 3
          scanline_data[pos] = (a << 24) + r + g + b
          pos += 1
        end
      end
      subimages << { :w => width, :h => height, :data => scanline_data }
      subimage_base += 40 + (width * height * 3)
    end
    puts "  Total Dimensions: #{max_width}x#{total_height}"
    
    image = BufferedImage.new(max_width, total_height, BufferedImage::TYPE_INT_ARGB)
    cur_y = 0
    subimages.each do |subimage|
      image.setRGB(0, cur_y, subimage[:w], subimage[:h], subimage[:data].to_java(:int), 0, subimage[:w])
      cur_y += subimage[:h]
    end

    puts "  Done converting from srf..."

    return image
  end
end

class SrfConverter
  def initialize(view_builder)
    @view_builder = view_builder
    
    @loaded_file = nil

    @view_builder.set_callback :open, Proc.new { load_image(@view_builder.get_file_to_open) }
    @view_builder.set_callback :exit, Proc.new { java.lang.System.exit(0) }
  end

  def run
    main_frame = @view_builder.build
    main_frame.visible = true
  end
  
  def load_image(image_file)
    return if image_file.nil?
    @vehicle = VehicleImage.new(image_file)
    @view_builder.animation_panel.set_images(@vehicle.images_3d)
  end
end
